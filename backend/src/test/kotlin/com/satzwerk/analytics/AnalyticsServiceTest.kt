package com.satzwerk.analytics

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

class AnalyticsServiceTest {
    private val userId = UUID.randomUUID()

    private val emptySummaryRow =
        DashboardSummaryRow(
            totalSessions = 10,
            sessionsThisMonth = 2,
            setsThisWeek = 15,
            prsThisMonth = 1,
            activePlanDays = 30,
            avgSessionDurationMinutes = 45,
        )

    private fun service(
        summaryRow: DashboardSummaryRow = emptySummaryRow,
        workoutDays: List<LocalDate> = emptyList(),
    ): AnalyticsService {
        val repo =
            mock<AnalyticsRepository> {
                onBlocking { findDashboardSummary(any()) } doReturn summaryRow
                onBlocking { findWorkoutDays(any()) } doReturn workoutDays
            }
        return AnalyticsService(repo)
    }

    @Test
    fun `dashboardSummary returns zero streaks when no workout days`(): Unit =
        runBlocking {
            val result = service(workoutDays = emptyList()).dashboardSummary(userId)

            assertEquals(0, result.currentStreak)
            assertEquals(0, result.longestStreak)
        }

    @Test
    fun `dashboardSummary computes current streak when most recent workout is today`(): Unit =
        runBlocking {
            val today = LocalDate.now(ZoneOffset.UTC)
            val days = listOf(today, today.minusDays(1), today.minusDays(2))

            val result = service(workoutDays = days).dashboardSummary(userId)

            assertEquals(3, result.currentStreak)
        }

    @Test
    fun `dashboardSummary computes current streak when most recent workout is yesterday`(): Unit =
        runBlocking {
            val today = LocalDate.now(ZoneOffset.UTC)
            val days = listOf(today.minusDays(1), today.minusDays(2))

            val result = service(workoutDays = days).dashboardSummary(userId)

            assertEquals(2, result.currentStreak)
        }

    @Test
    fun `dashboardSummary computes longest streak from Kotlin calculator`(): Unit =
        runBlocking {
            val today = LocalDate.now(ZoneOffset.UTC)
            val days =
                listOf(
                    today.minusDays(1),
                    today.minusDays(2),
                    today.minusDays(3),
                    today.minusDays(5),
                )
            val result = service(workoutDays = days).dashboardSummary(userId)

            assertEquals(3, result.longestStreak)
        }

    @Test
    fun `dashboardSummary computes current streak as zero when most recent day is not today or yesterday`(): Unit =
        runBlocking {
            val today = LocalDate.now(ZoneOffset.UTC)
            val days = listOf(today.minusDays(2), today.minusDays(3))

            val result = service(workoutDays = days).dashboardSummary(userId)

            assertEquals(0, result.currentStreak)
        }

    @Test
    fun `dashboardSummary forwards non-streak fields from repository row`(): Unit =
        runBlocking {
            val result = service().dashboardSummary(userId)

            assertEquals(10, result.totalSessions)
            assertEquals(2, result.sessionsThisMonth)
            assertEquals(15, result.setsThisWeek)
        }

    @Test
    fun `topExercises maps repository rows to TopExercise domain objects`(): Unit =
        runBlocking {
            val exerciseId1 = UUID.randomUUID()
            val exerciseId2 = UUID.randomUUID()
            val rows =
                listOf(
                    TopExerciseRow(exerciseId = exerciseId1, exerciseName = "Bench Press", setCount = 42),
                    TopExerciseRow(exerciseId = exerciseId2, exerciseName = "Squat", setCount = 35),
                )
            val repo =
                mock<AnalyticsRepository> {
                    onBlocking { findDashboardSummary(any()) } doReturn emptySummaryRow
                    onBlocking { findWorkoutDays(any()) } doReturn emptyList()
                    onBlocking { findTopExercisesBySetCount(any(), any()) } doReturn rows
                }
            val result = AnalyticsService(repo).topExercises(userId, limit = 2)

            assertEquals(2, result.size)
            assertEquals("Bench Press", result[0].exerciseName)
            assertEquals(42, result[0].setCount)
            assertEquals(exerciseId1, result[0].exerciseId)
            assertEquals("Squat", result[1].exerciseName)
            assertEquals(35, result[1].setCount)
        }
}
