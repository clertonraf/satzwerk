package com.satzwerk.analytics

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.util.UUID

@Service
class AnalyticsService(
    private val databaseClient: DatabaseClient,
) {
    suspend fun heatmap(
        userId: UUID,
        from: LocalDate,
        to: LocalDate,
    ): List<HeatmapEntry> {
        val countsByDate =
            databaseClient
                .sql(
                    """
                    SELECT DATE(sl.logged_at AT TIME ZONE 'UTC') AS day, COUNT(*) AS cnt
                    FROM set_logs sl
                    JOIN workout_sessions ws ON sl.workout_session_id = ws.id
                    WHERE ws.user_id = :userId
                      AND DATE(sl.logged_at AT TIME ZONE 'UTC') BETWEEN :from AND :to
                    GROUP BY day
                    """.trimIndent(),
                ).bind("userId", userId)
                .bind("from", from)
                .bind("to", to)
                .map { row, _ ->
                    val day = row.get("day", LocalDate::class.java)!!
                    val count = row.get("cnt", java.lang.Long::class.java)?.toInt() ?: 0
                    day to count
                }.all()
                .collectList()
                .awaitSingle()
                .toMap()

        return from.datesUntil(to.plusDays(1))
            .map { date ->
                val count = countsByDate[date] ?: 0
                HeatmapEntry(date = date, count = count, intensity = intensityTier(count))
            }.toList()
    }

    suspend fun streak(userId: UUID): StreakResponse {
        val days =
            databaseClient
                .sql(
                    """
                    SELECT DISTINCT DATE(sl.logged_at AT TIME ZONE 'UTC') AS day
                    FROM set_logs sl
                    JOIN workout_sessions ws ON sl.workout_session_id = ws.id
                    WHERE ws.user_id = :userId
                    ORDER BY day DESC
                    """.trimIndent(),
                ).bind("userId", userId)
                .map { row, _ -> row.get("day", LocalDate::class.java)!! }
                .all()
                .collectList()
                .awaitSingle()

        if (days.isEmpty()) {
            return StreakResponse(currentStreak = 0, longestStreak = 0)
        }

        val longestStreak = longestStreak(days)
        val today = LocalDate.now(java.time.ZoneOffset.UTC)
        val currentStreak =
            if (days.first() == today || days.first() == today.minusDays(1)) {
                leadingStreak(days)
            } else {
                0
            }

        return StreakResponse(currentStreak = currentStreak, longestStreak = longestStreak)
    }
}

private fun longestStreak(days: List<LocalDate>): Int {
    var longest = 1
    var streak = 1

    for (index in 1 until days.size) {
        if (days[index - 1].minusDays(1) == days[index]) {
            streak += 1
            if (streak > longest) {
                longest = streak
            }
        } else {
            streak = 1
        }
    }

    return longest
}

private fun leadingStreak(days: List<LocalDate>): Int {
    var streak = 1

    for (index in 1 until days.size) {
        if (days[index - 1].minusDays(1) == days[index]) {
            streak += 1
        } else {
            break
        }
    }

    return streak
}
