package com.satzwerk.analytics

import com.satzwerk.common.NotFoundException
import com.satzwerk.sessions.epley
import com.satzwerk.workouts.ExerciseRepository
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

private const val DEFAULT_PR_LIMIT = 5
private const val DEFAULT_TOP_EXERCISES_LIMIT = 5
private const val DEFAULT_TREND_WEEKS = 8
private const val RECENT_SESSIONS_LIMIT = 5

@Service
class AnalyticsService(
    private val analyticsRepository: AnalyticsRepository,
    private val exerciseRepository: ExerciseRepository,
) {
    suspend fun heatmap(
        userId: UUID,
        from: LocalDate,
        to: LocalDate,
    ): List<HeatmapEntry> {
        val countsByDate = analyticsRepository.findSetCountsByDate(userId, from, to)

        return from.datesUntil(to.plusDays(1))
            .map { date ->
                val count = countsByDate[date] ?: 0
                HeatmapEntry(date = date, count = count, intensity = intensityTier(count))
            }.toList()
    }

    suspend fun streak(userId: UUID): StreakResponse {
        val days = analyticsRepository.findWorkoutDays(userId)
        val (current, longest) = computeStreaks(days)
        return StreakResponse(currentStreak = current, longestStreak = longest)
    }

    suspend fun dashboardSummary(userId: UUID): DashboardSummary {
        val summaryRow = analyticsRepository.findDashboardSummary(userId)
        val days = analyticsRepository.findWorkoutDays(userId)
        val (currentStreak, longestStreak) = computeStreaks(days)
        return DashboardSummary(
            currentStreak = currentStreak,
            longestStreak = longestStreak,
            sessionsThisMonth = summaryRow.sessionsThisMonth,
            setsThisWeek = summaryRow.setsThisWeek,
            totalSessions = summaryRow.totalSessions,
            prsThisMonth = summaryRow.prsThisMonth,
            activePlanDays = summaryRow.activePlanDays,
            avgSessionDurationMinutes = summaryRow.avgSessionDurationMinutes,
        )
    }

    suspend fun weeklyTrend(
        userId: UUID,
        weeks: Int = DEFAULT_TREND_WEEKS,
    ): List<WeeklyTrendEntry> =
        analyticsRepository.findWeeklyTrend(userId, weeks).map { row ->
            WeeklyTrendEntry(week = row.week, setCount = row.setCount, sessionCount = row.sessionCount)
        }

    suspend fun personalRecords(
        userId: UUID,
        limit: Int = DEFAULT_PR_LIMIT,
    ): List<PersonalRecord> =
        analyticsRepository.findRecentPersonalRecords(userId, limit).map { row ->
            PersonalRecord(
                exerciseId = row.exerciseId,
                exerciseName = row.exerciseName,
                weightKg = row.weightKg,
                reps = row.reps,
                achievedAt = row.achievedAt,
            )
        }

    suspend fun topExercises(
        userId: UUID,
        limit: Int = DEFAULT_TOP_EXERCISES_LIMIT,
    ): List<TopExercise> =
        analyticsRepository.findTopExercisesBySetCount(userId, limit).map { row ->
            TopExercise(
                exerciseId = row.exerciseId,
                exerciseName = row.exerciseName,
                setCount = row.setCount,
            )
        }

    suspend fun leastExercises(
        userId: UUID,
        limit: Int = DEFAULT_TOP_EXERCISES_LIMIT,
    ): List<TopExercise> =
        analyticsRepository.findLeastExercisesBySetCount(userId, limit).map { row ->
            TopExercise(
                exerciseId = row.exerciseId,
                exerciseName = row.exerciseName,
                setCount = row.setCount,
            )
        }

    suspend fun exerciseProgress(
        userId: UUID,
        exerciseId: UUID,
    ): ExerciseProgressResponse {
        val rows = analyticsRepository.findExerciseProgress(userId, exerciseId)
        if (rows.isEmpty()) {
            val exercise =
                exerciseRepository.findByIdAndUserId(exerciseId, userId)
                    ?: throw NotFoundException("Exercise $exerciseId not found")
            return ExerciseProgressResponse(
                exerciseId = exerciseId,
                exerciseName = exercise.name,
                points = emptyList(),
                recentSessions = emptyList(),
            )
        }
        return ExerciseProgressResponse(
            exerciseId = exerciseId,
            exerciseName = rows.first().exerciseName,
            points =
                rows.map { row ->
                    ExerciseProgressPoint(
                        sessionId = row.sessionId,
                        sessionDate = row.sessionDate,
                        topSetWeightKg = row.topSetWeightKg,
                        topSetReps = row.topSetReps,
                        estimatedOneRepMaxKg = epley(row.topSetWeightKg, row.topSetReps),
                    )
                },
            recentSessions =
                rows.takeLast(RECENT_SESSIONS_LIMIT).reversed().map { row ->
                    ExerciseProgressSessionSummary(
                        sessionId = row.sessionId,
                        sessionDate = row.sessionDate,
                        workoutGroupTitle = row.workoutGroupTitle,
                        topSetLabel =
                            "${row.topSetWeightKg.stripTrailingZeros().toPlainString()} kg × ${row.topSetReps}",
                    )
                },
        )
    }
}

/**
 * Computes current and longest workout streaks from a list of workout days (descending order).
 * [days] should be the full workout-day history from [AnalyticsRepository.findWorkoutDays].
 * Returns Pair(currentStreak, longestStreak).
 */
internal fun computeStreaks(days: List<LocalDate>): Pair<Int, Int> {
    if (days.isEmpty()) return 0 to 0
    val longest = longestStreak(days)
    val today = LocalDate.now(ZoneOffset.UTC)
    val current =
        if (days.first() == today || days.first() == today.minusDays(1)) {
            leadingStreak(days)
        } else {
            0
        }
    return current to longest
}
