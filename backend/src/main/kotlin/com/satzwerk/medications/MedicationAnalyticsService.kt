package com.satzwerk.medications

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

private const val STREAK_WINDOW_DAYS = 30L
private const val PER_MED_HEATMAP_WEEKS = 52L
private const val WEEKLY_BAR_CHART_WEEKS = 12L
private const val MONTHLY_BAR_CHART_MONTHS = 6L

@Service
class MedicationAnalyticsService(
    private val medicationRepository: MedicationRepository,
    private val medicationLogRepository: MedicationLogRepository,
    private val objectMapper: ObjectMapper,
) {
    suspend fun getAdherenceStreak(medicationId: UUID): Int {
        val today = LocalDate.now(ZoneOffset.UTC)
        val thirtyDaysAgo = today.minusDays(STREAK_WINDOW_DAYS)
        val from = thirtyDaysAgo.atStartOfDay(ZoneOffset.UTC).toInstant()
        val to = today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()

        val logs =
            medicationLogRepository.findByMedicationIdAndTakenAtBetweenOrderByTakenAtDesc(
                medicationId,
                from,
                to,
            )

        val takenDays =
            logs.filter { it.taken }
                .map { it.takenAt.atZone(ZoneOffset.UTC).toLocalDate() }
                .distinct()
                .sortedDescending()

        if (takenDays.isEmpty()) return 0

        var streak = 0
        var expected = today
        for (day in takenDays) {
            if (day == expected) {
                streak++
                expected = expected.minusDays(1)
            } else {
                break
            }
        }
        return streak
    }

    suspend fun getAggregateHeatmap(
        userId: UUID,
        weeks: Int,
    ): AdherenceHeatmapDto {
        val today = LocalDate.now(ZoneOffset.UTC)
        val startDate = today.minusWeeks(weeks.toLong())
        val from = startDate.atStartOfDay(ZoneOffset.UTC).toInstant()
        val to = today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()

        val medications = medicationRepository.findByUserIdOrderByNameAsc(userId).filter { it.isActive }
        val logs = medicationLogRepository.findByUserIdAndTakenAtBetweenOrderByTakenAtDesc(userId, from, to)

        val logsByDay =
            logs.groupBy { it.takenAt.atZone(ZoneOffset.UTC).toLocalDate() }

        val days = mutableListOf<AdherenceHeatmapDayDto>()
        var current = startDate
        while (!current.isAfter(today)) {
            val dayLogs = logsByDay[current] ?: emptyList()
            val scheduled =
                medications.count { med ->
                    isDueToday(deserializeFrequency(med.frequency, objectMapper), current)
                }
            val taken = dayLogs.count { it.taken }
            val ratio = if (scheduled > 0) taken.toDouble() / scheduled.toDouble() else 0.0
            days.add(
                AdherenceHeatmapDayDto(
                    date = current.format(DateTimeFormatter.ISO_LOCAL_DATE),
                    adherenceRatio = ratio.coerceIn(0.0, 1.0),
                    takenCount = taken,
                    scheduledCount = scheduled,
                ),
            )
            current = current.plusDays(1)
        }
        return AdherenceHeatmapDto(days = days)
    }

    suspend fun getPerMedicationAnalytics(
        medicationId: UUID,
        userId: UUID,
        granularity: BarChartGranularity,
    ): PerMedicationAnalyticsDto {
        val streak = getAdherenceStreak(medicationId)
        val heatmap = buildPerMedicationHeatmap(medicationId, userId)
        val barChart = buildBarChart(medicationId, granularity)
        return PerMedicationAnalyticsDto(heatmap = heatmap, barChart = barChart, currentStreak = streak)
    }

    private suspend fun buildPerMedicationHeatmap(
        medicationId: UUID,
        userId: UUID,
    ): AdherenceHeatmapDto {
        val today = LocalDate.now(ZoneOffset.UTC)
        val startDate = today.minusWeeks(PER_MED_HEATMAP_WEEKS)
        val from = startDate.atStartOfDay(ZoneOffset.UTC).toInstant()
        val to = today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()

        val medication = requireOwnedMedication(medicationRepository, userId, medicationId)
        val spec = deserializeFrequency(medication.frequency, objectMapper)
        val logs =
            medicationLogRepository.findByMedicationIdAndTakenAtBetweenOrderByTakenAtDesc(
                medicationId,
                from,
                to,
            )
        val logsByDay = logs.groupBy { it.takenAt.atZone(ZoneOffset.UTC).toLocalDate() }

        val days = mutableListOf<AdherenceHeatmapDayDto>()
        var current = startDate
        while (!current.isAfter(today)) {
            val scheduled = if (isDueToday(spec, current)) scheduledCountForToday(spec) else 0
            val dayLogs = logsByDay[current] ?: emptyList()
            val taken = dayLogs.count { it.taken }
            val ratio = if (scheduled > 0) taken.toDouble() / scheduled.toDouble() else 0.0
            days.add(
                AdherenceHeatmapDayDto(
                    date = current.format(DateTimeFormatter.ISO_LOCAL_DATE),
                    adherenceRatio = ratio.coerceIn(0.0, 1.0),
                    takenCount = taken,
                    scheduledCount = scheduled,
                ),
            )
            current = current.plusDays(1)
        }
        return AdherenceHeatmapDto(days = days)
    }

    private suspend fun buildBarChart(
        medicationId: UUID,
        granularity: BarChartGranularity,
    ): List<BarChartPeriodDto> {
        val today = LocalDate.now(ZoneOffset.UTC)
        val startDate =
            if (granularity == BarChartGranularity.WEEKLY) {
                today.minusWeeks(WEEKLY_BAR_CHART_WEEKS)
            } else {
                today.minusMonths(MONTHLY_BAR_CHART_MONTHS)
            }
        val from = startDate.atStartOfDay(ZoneOffset.UTC).toInstant()
        val to = today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()

        val logs =
            medicationLogRepository.findByMedicationIdAndTakenAtBetweenOrderByTakenAtDesc(
                medicationId,
                from,
                to,
            )

        return if (granularity == BarChartGranularity.WEEKLY) {
            buildWeeklyBarChart(logs)
        } else {
            buildMonthlyBarChart(logs)
        }
    }

    private fun buildWeeklyBarChart(logs: List<MedicationLog>): List<BarChartPeriodDto> =
        logs.groupBy {
            val d = it.takenAt.atZone(ZoneOffset.UTC).toLocalDate()
            "${d.year}-W${d.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear())}"
        }.map { (period, periodLogs) ->
            BarChartPeriodDto(
                period = period,
                taken = periodLogs.count { it.taken },
                skipped = periodLogs.count { !it.taken },
            )
        }.sortedBy { it.period }

    private fun buildMonthlyBarChart(logs: List<MedicationLog>): List<BarChartPeriodDto> =
        logs.groupBy {
            val d = it.takenAt.atZone(ZoneOffset.UTC).toLocalDate()
            "${d.year}-${d.monthValue.toString().padStart(2, '0')}"
        }.map { (period, periodLogs) ->
            BarChartPeriodDto(
                period = period,
                taken = periodLogs.count { it.taken },
                skipped = periodLogs.count { !it.taken },
            )
        }.sortedBy { it.period }
}
