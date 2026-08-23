package com.satzwerk.medications

import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val adherenceHeatmapDateFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

fun buildAdherenceHeatmapDays(
    startDate: LocalDate,
    endDate: LocalDate,
    logsByDay: Map<LocalDate, List<MedicationLog>>,
    scheduledCountOn: (LocalDate) -> Int,
): List<AdherenceHeatmapDayDto> {
    val days = mutableListOf<AdherenceHeatmapDayDto>()
    var current = startDate

    while (!current.isAfter(endDate)) {
        val scheduled = scheduledCountOn(current)
        val taken = (logsByDay[current] ?: emptyList()).count { it.taken }
        val ratio = if (scheduled > 0) taken.toDouble() / scheduled.toDouble() else 0.0

        days.add(
            AdherenceHeatmapDayDto(
                date = current.format(adherenceHeatmapDateFormatter),
                adherenceRatio = ratio.coerceIn(0.0, 1.0),
                takenCount = taken,
                scheduledCount = scheduled,
            ),
        )
        current = current.plusDays(1)
    }

    return days
}
