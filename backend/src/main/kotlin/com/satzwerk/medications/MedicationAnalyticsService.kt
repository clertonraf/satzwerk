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

/** Returns the streak (consecutive scheduled days with all doses taken) ending today. */
fun computeAdherenceStreak(
    spec: FrequencySpec,
    logsByDay: Map<LocalDate, List<MedicationLog>>,
    today: LocalDate,
): Int =
    (0..STREAK_WINDOW_DAYS.toInt())
        .asSequence()
        .map { daysBack -> today.minusDays(daysBack.toLong()) }
        .filter { day -> isDueToday(spec, day) }
        .takeWhile { day ->
            val required = scheduledCountForToday(spec)
            val taken = logsByDay[day]?.count { it.taken } ?: 0
            taken >= required
        }
        .count()

@Service
class MedicationAnalyticsService(
    private val medicationRepository: MedicationRepository,
    private val medicationLogRepository: MedicationLogRepository,
    private val objectMapper: ObjectMapper,
) {
    suspend fun getAdherenceStreak(medicationId: UUID): Int {
        val medication = medicationRepository.findById(medicationId) ?: return 0
        val spec = deserializeFrequency(medication.frequency, objectMapper)
        val today = LocalDate.now(ZoneOffset.UTC)
        val from = today.minusDays(STREAK_WINDOW_DAYS).atStartOfDay(ZoneOffset.UTC).toInstant()
        val to = today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
        val logs =
            medicationLogRepository.findByMedicationIdAndTakenAtBetweenOrderByTakenAtDesc(medicationId, from, to)
        val logsByDay = logs.groupBy { it.takenAt.atZone(ZoneOffset.UTC).toLocalDate() }
        return computeAdherenceStreak(spec, logsByDay, today)
    }

    suspend fun getAdherenceStreaksBatch(medications: List<Medication>): Map<UUID, Int> {
        if (medications.isEmpty()) return emptyMap()
        val today = LocalDate.now(ZoneOffset.UTC)
        val from = today.minusDays(STREAK_WINDOW_DAYS).atStartOfDay(ZoneOffset.UTC).toInstant()
        val to = today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
        val ids = medications.mapNotNull { it.id }
        val allLogs =
            medicationLogRepository.findByMedicationIdInAndTakenAtBetweenOrderByTakenAtDesc(ids, from, to)
        val logsByMed = allLogs.groupBy { it.medicationId }
        return medications.associate { med ->
            val id = requireNotNull(med.id)
            val spec = deserializeFrequency(med.frequency, objectMapper)
            val logsByDay =
                (logsByMed[id] ?: emptyList())
                    .groupBy { it.takenAt.atZone(ZoneOffset.UTC).toLocalDate() }
            id to computeAdherenceStreak(spec, logsByDay, today)
        }
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
        val medicationSpecs = medications.map { med -> med to deserializeFrequency(med.frequency, objectMapper) }
        val logs = medicationLogRepository.findByUserIdAndTakenAtBetweenOrderByTakenAtDesc(userId, from, to)
        val logsByDay = logs.groupBy { it.takenAt.atZone(ZoneOffset.UTC).toLocalDate() }

        val days = mutableListOf<AdherenceHeatmapDayDto>()
        var current = startDate
        while (!current.isAfter(today)) {
            val dayLogs = logsByDay[current] ?: emptyList()
            val scheduled =
                medicationSpecs.sumOf { (_, spec) ->
                    if (isDueToday(spec, current)) scheduledCountForToday(spec) else 0
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
        val heatmap = buildPerMedicationHeatmap(medicationId, userId)
        val streak = getAdherenceStreak(medicationId)
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
            val isoYear = d.get(java.time.temporal.WeekFields.ISO.weekBasedYear())
            val isoWeek = d.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear())
            "$isoYear-W${isoWeek.toString().padStart(2, '0')}"
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
