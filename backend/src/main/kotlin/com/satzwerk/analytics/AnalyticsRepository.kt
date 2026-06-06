package com.satzwerk.analytics

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private const val DEFAULT_WEEKLY_TREND_WEEKS = 8

@Repository
class AnalyticsRepository(
    private val databaseClient: DatabaseClient,
) {
    suspend fun findSetCountsByDate(
        userId: UUID,
        from: LocalDate,
        to: LocalDate,
    ): Map<LocalDate, Int> =
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
            .asFlow()
            .toList()
            .toMap()

    suspend fun findWorkoutDays(userId: UUID): List<LocalDate> =
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
            .asFlow()
            .toList()

    suspend fun findDashboardSummary(userId: UUID): DashboardSummaryRow =
        databaseClient
            .sql(
                """
                SELECT
                    (SELECT COUNT(*) FROM workout_sessions
                     WHERE user_id = :userId AND completed_at IS NOT NULL) AS total_sessions,
                    (SELECT COUNT(*) FROM workout_sessions
                     WHERE user_id = :userId
                       AND completed_at IS NOT NULL
                       AND DATE_TRUNC('month', completed_at AT TIME ZONE 'UTC')
                           = DATE_TRUNC('month', NOW() AT TIME ZONE 'UTC')) AS sessions_this_month,
                    (SELECT COUNT(*) FROM set_logs sl
                     JOIN workout_sessions ws ON sl.workout_session_id = ws.id
                     WHERE ws.user_id = :userId
                       AND DATE_TRUNC('week', sl.logged_at AT TIME ZONE 'UTC')
                           = DATE_TRUNC('week', NOW() AT TIME ZONE 'UTC')) AS sets_this_week,
                    (SELECT COUNT(*) FROM set_logs sl
                     JOIN workout_sessions ws ON sl.workout_session_id = ws.id
                     WHERE ws.user_id = :userId
                       AND sl.is_pr = TRUE
                       AND DATE_TRUNC('month', sl.logged_at AT TIME ZONE 'UTC')
                           = DATE_TRUNC('month', NOW() AT TIME ZONE 'UTC')) AS prs_this_month,
                    (SELECT EXTRACT(DAY FROM NOW() - activated_at)::INT
                     FROM workout_plans
                     WHERE user_id = :userId AND is_active = TRUE
                     LIMIT 1) AS active_plan_days
                """.trimIndent(),
            ).bind("userId", userId)
            .map { row, _ ->
                DashboardSummaryRow(
                    totalSessions = row.get("total_sessions", java.lang.Long::class.java)?.toInt() ?: 0,
                    sessionsThisMonth = row.get("sessions_this_month", java.lang.Long::class.java)?.toInt() ?: 0,
                    setsThisWeek = row.get("sets_this_week", java.lang.Long::class.java)?.toInt() ?: 0,
                    prsThisMonth = row.get("prs_this_month", java.lang.Long::class.java)?.toInt() ?: 0,
                    activePlanDays = row.get("active_plan_days", java.lang.Integer::class.java)?.toInt(),
                )
            }.one()
            .awaitSingle()

    suspend fun findWeeklyTrend(
        userId: UUID,
        weeks: Int = DEFAULT_WEEKLY_TREND_WEEKS,
    ): List<WeeklyTrendRow> =
        databaseClient
            .sql(
                """
                WITH week_series AS (
                    SELECT generate_series(
                        DATE_TRUNC('week', NOW() AT TIME ZONE 'UTC') - (:weeks - 1) * INTERVAL '1 week',
                        DATE_TRUNC('week', NOW() AT TIME ZONE 'UTC'),
                        INTERVAL '1 week'
                    ) AS week_start
                ),
                set_counts AS (
                    SELECT DATE_TRUNC('week', sl.logged_at AT TIME ZONE 'UTC') AS week_start,
                           COUNT(*) AS set_count
                    FROM set_logs sl
                    JOIN workout_sessions ws ON sl.workout_session_id = ws.id
                    WHERE ws.user_id = :userId
                      AND sl.logged_at >= DATE_TRUNC('week', NOW() AT TIME ZONE 'UTC') - (:weeks - 1) * INTERVAL '1 week'
                    GROUP BY 1
                ),
                session_counts AS (
                    SELECT DATE_TRUNC('week', completed_at AT TIME ZONE 'UTC') AS week_start,
                           COUNT(*) AS session_count
                    FROM workout_sessions
                    WHERE user_id = :userId
                      AND completed_at IS NOT NULL
                      AND completed_at >= DATE_TRUNC('week', NOW() AT TIME ZONE 'UTC') - (:weeks - 1) * INTERVAL '1 week'
                    GROUP BY 1
                )
                SELECT
                    TO_CHAR(ws.week_start, 'IYYY-"W"IW') AS week,
                    COALESCE(sc.set_count, 0) AS set_count,
                    COALESCE(sess.session_count, 0) AS session_count
                FROM week_series ws
                LEFT JOIN set_counts sc ON sc.week_start = ws.week_start
                LEFT JOIN session_counts sess ON sess.week_start = ws.week_start
                ORDER BY ws.week_start
                """.trimIndent(),
            ).bind("userId", userId)
            .bind("weeks", weeks)
            .map { row, _ ->
                WeeklyTrendRow(
                    week = row.get("week", String::class.java)!!,
                    setCount = row.get("set_count", java.lang.Long::class.java)?.toInt() ?: 0,
                    sessionCount = row.get("session_count", java.lang.Long::class.java)?.toInt() ?: 0,
                )
            }.all()
            .asFlow()
            .toList()

    suspend fun findRecentPersonalRecords(
        userId: UUID,
        limit: Int,
    ): List<PersonalRecordRow> =
        databaseClient
            .sql(
                """
                SELECT sl.id, sl.exercise_id, e.name AS exercise_name,
                       sl.weight, sl.logged_at
                FROM set_logs sl
                JOIN workout_sessions ws ON sl.workout_session_id = ws.id
                JOIN exercises e ON sl.exercise_id = e.id
                WHERE ws.user_id = :userId
                  AND sl.is_pr = TRUE
                ORDER BY sl.logged_at DESC
                LIMIT :limit
                """.trimIndent(),
            ).bind("userId", userId)
            .bind("limit", limit)
            .map { row, _ ->
                PersonalRecordRow(
                    exerciseId = row.get("exercise_id", UUID::class.java)!!,
                    exerciseName = row.get("exercise_name", String::class.java)!!,
                    weightKg = row.get("weight", BigDecimal::class.java)!!,
                    achievedAt = row.get("logged_at", Instant::class.java)!!,
                )
            }.all()
            .asFlow()
            .toList()
}

data class DashboardSummaryRow(
    val totalSessions: Int,
    val sessionsThisMonth: Int,
    val setsThisWeek: Int,
    val prsThisMonth: Int,
    val activePlanDays: Int?,
)

data class WeeklyTrendRow(
    val week: String,
    val setCount: Int,
    val sessionCount: Int,
)

data class PersonalRecordRow(
    val exerciseId: UUID,
    val exerciseName: String,
    val weightKg: BigDecimal,
    val achievedAt: Instant,
)
