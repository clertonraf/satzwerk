package com.satzwerk.analytics

import com.satzwerk.workouts.WorkoutReadPort
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import java.time.LocalDate
import java.util.UUID

class PublicAnalyticsServiceTest {
    @Test
    fun `heatmap returns zero-filled range from workout read port`(): Unit =
        runBlocking {
            val userId = UUID.randomUUID()
            val from = LocalDate.of(2026, 1, 1)
            val to = LocalDate.of(2026, 1, 3)
            val workoutReadPort =
                mock<WorkoutReadPort> {
                    onBlocking { findSetCountsByDate(eq(userId), eq(from), eq(to)) } doReturn
                        mapOf(
                            from.plusDays(1) to 5,
                        )
                }

            val result = PublicAnalyticsService(workoutReadPort).heatmap(userId, from, to)

            assertEquals(
                listOf(
                    HeatmapEntry(date = from, count = 0, intensity = 0),
                    HeatmapEntry(date = from.plusDays(1), count = 5, intensity = 2),
                    HeatmapEntry(date = to, count = 0, intensity = 0),
                ),
                result,
            )
        }
}
