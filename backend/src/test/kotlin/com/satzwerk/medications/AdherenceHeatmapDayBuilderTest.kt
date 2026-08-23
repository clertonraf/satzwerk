package com.satzwerk.medications

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

class AdherenceHeatmapDayBuilderTest {
    private val medicationId = UUID.randomUUID()
    private val userId = UUID.randomUUID()

    @Test
    fun `buildAdherenceHeatmapDays returns inclusive days with derived adherence counts`() {
        val startDate = LocalDate.of(2026, 1, 1)
        val middleDate = startDate.plusDays(1)
        val endDate = startDate.plusDays(2)

        val days =
            buildAdherenceHeatmapDays(
                startDate = startDate,
                endDate = endDate,
                logsByDay =
                    mapOf(
                        startDate to listOf(logAt(startDate, taken = true), logAt(startDate, taken = false)),
                        middleDate to listOf(logAt(middleDate, taken = true)),
                        endDate to listOf(logAt(endDate, taken = true), logAt(endDate, taken = true)),
                    ),
            ) { day ->
                when (day) {
                    startDate -> 2
                    middleDate -> 0
                    endDate -> 1
                    else -> 0
                }
            }

        assertEquals(
            listOf(
                AdherenceHeatmapDayDto(
                    date = "2026-01-01",
                    adherenceRatio = 0.5,
                    takenCount = 1,
                    scheduledCount = 2,
                ),
                AdherenceHeatmapDayDto(
                    date = "2026-01-02",
                    adherenceRatio = 0.0,
                    takenCount = 1,
                    scheduledCount = 0,
                ),
                AdherenceHeatmapDayDto(
                    date = "2026-01-03",
                    adherenceRatio = 1.0,
                    takenCount = 2,
                    scheduledCount = 1,
                ),
            ),
            days,
        )
    }

    private fun logAt(
        date: LocalDate,
        taken: Boolean,
    ): MedicationLog =
        MedicationLog(
            id = UUID.randomUUID(),
            medicationId = medicationId,
            userId = userId,
            takenAt = date.atStartOfDay(ZoneOffset.UTC).plusHours(8).toInstant(),
            taken = taken,
        )
}
