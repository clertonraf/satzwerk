package com.satzwerk.workouts

import com.satzwerk.analytics.DashboardSummaryRow
import com.satzwerk.analytics.ExerciseProgressRow
import com.satzwerk.analytics.PersonalRecordRow
import com.satzwerk.analytics.TopExerciseRow
import com.satzwerk.analytics.WeeklyTrendRow
import com.satzwerk.sessions.SetLog
import com.satzwerk.sessions.SetLogRepository
import com.satzwerk.sessions.WorkoutSession
import com.satzwerk.sessions.WorkoutSessionRepository
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private const val DEFAULT_WEEKLY_TREND_WEEKS = 8

private val DASHBOARD_SUMMARY_SQL =
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
        LIMIT 1) AS active_plan_days,
        (SELECT CAST(ROUND(EXTRACT(EPOCH FROM AVG(completed_at - started_at)) / 60) AS INT)
        FROM workout_sessions
        WHERE user_id = :userId AND completed_at IS NOT NULL) AS avg_session_duration_minutes
    """.trimIndent()

data class WorkoutPlanExportData(
    val plan: WorkoutPlan,
    val groups: List<WorkoutGroupExportData>,
)

data class WorkoutGroupExportData(
    val group: WorkoutGroup,
    val exercises: List<WorkoutExercise>,
)

data class WorkoutSessionExportData(
    val session: WorkoutSession,
    val setLogs: List<SetLog>,
)

@Component
class WorkoutReadPort(
    private val workoutPlanRepository: WorkoutPlanRepository,
    private val workoutGroupRepository: WorkoutGroupRepository,
    private val workoutExerciseRepository: WorkoutExerciseRepository,
    private val workoutSessionRepository: WorkoutSessionRepository,
    private val setLogRepository: SetLogRepository,
    private val databaseClient: DatabaseClient,
) {
    suspend fun findOpenSession(userId: UUID): WorkoutSession? =
        workoutSessionRepository.findByUserIdAndCompletedAtIsNull(userId)

    suspend fun findExportPlans(userId: UUID): List<WorkoutPlanExportData> =
        workoutPlanRepository.findAllByUserId(userId).map { plan ->
            val groups =
                workoutGroupRepository.findAllByWorkoutPlanIdOrderByOrderIndex(requireNotNull(plan.id)).map { group ->
                    WorkoutGroupExportData(
                        group = group,
                        exercises =
                            workoutExerciseRepository.findAllByWorkoutGroupIdOrderByOrderIndex(
                                requireNotNull(group.id),
                            ),
                    )
                }
            WorkoutPlanExportData(plan = plan, groups = groups)
        }

    suspend fun findExportSessions(userId: UUID): List<WorkoutSessionExportData> {
        val sessions = workoutSessionRepository.findAllByUserId(userId)
        val logsBySession =
            setLogRepository
                .findAllByWorkoutSessionIdIn(sessions.mapNotNull { it.id })
                .groupBy { it.workoutSessionId }

        return sessions.map { session ->
            WorkoutSessionExportData(session = session, setLogs = logsBySession[session.id] ?: emptyList())
        }
    }

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
            .sql(DASHBOARD_SUMMARY_SQL)
            .bind("userId", userId)
            .map { row, _ ->
                DashboardSummaryRow(
                    totalSessions = row.get("total_sessions", java.lang.Long::class.java)?.toInt() ?: 0,
                    sessionsThisMonth = row.get("sessions_this_month", java.lang.Long::class.java)?.toInt() ?: 0,
                    setsThisWeek = row.get("sets_this_week", java.lang.Long::class.java)?.toInt() ?: 0,
                    prsThisMonth = row.get("prs_this_month", java.lang.Long::class.java)?.toInt() ?: 0,
                    activePlanDays = row.get("active_plan_days", java.lang.Integer::class.java)?.toInt(),
                    avgSessionDurationMinutes =
                        row.get("avg_session_duration_minutes", java.lang.Integer::class.java)?.toInt(),
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
                       sl.weight, sl.reps, sl.logged_at
                FROM set_logs sl
                JOIN workout_sessions ws ON sl.workout_session_id = ws.id
                JOIN exercises e ON sl.exercise_id = e.id
                WHERE ws.user_id = :userId
                  AND sl.is_pr = TRUE
                ORDER BY sl.logged_at DESC, sl.id DESC
                LIMIT :limit
                """.trimIndent(),
            ).bind("userId", userId)
            .bind("limit", limit)
            .map { row, _ ->
                PersonalRecordRow(
                    exerciseId = row.get("exercise_id", UUID::class.java)!!,
                    exerciseName = row.get("exercise_name", String::class.java)!!,
                    weightKg = row.get("weight", BigDecimal::class.java)!!,
                    reps = requireNotNull(row.get("reps", Integer::class.java)?.toInt()) { "reps must not be null" },
                    achievedAt = row.get("logged_at", Instant::class.java)!!,
                )
            }.all()
            .asFlow()
            .toList()

    suspend fun findExercisesBySetCount(
        userId: UUID,
        limit: Int,
        ascending: Boolean,
    ): List<TopExerciseRow> = selectExercisesBySetCount(databaseClient, userId, limit, ascending)

    suspend fun findExerciseProgress(
        userId: UUID,
        exerciseId: UUID,
    ): List<ExerciseProgressRow> =
        databaseClient
            .sql(
                """
                WITH ranked_sets AS (
                    SELECT
                        ws.id AS session_id,
                        DATE(ws.completed_at AT TIME ZONE 'UTC') AS session_date,
                        ws.completed_at AS session_completed_at,
                        wg.title AS workout_group_title,
                        sl.exercise_id,
                        e.name AS exercise_name,
                        sl.weight AS top_set_weight_kg,
                        sl.reps AS top_set_reps,
                        ROW_NUMBER() OVER (
                            PARTITION BY ws.id, sl.exercise_id
                            ORDER BY sl.weight DESC, sl.reps DESC, sl.logged_at DESC, sl.id DESC
                        ) AS rank_in_session
                    FROM set_logs sl
                    JOIN workout_sessions ws ON sl.workout_session_id = ws.id
                    JOIN workout_groups wg ON ws.workout_group_id = wg.id
                    JOIN exercises e ON sl.exercise_id = e.id AND e.user_id = :userId
                    WHERE ws.user_id = :userId
                      AND ws.completed_at IS NOT NULL
                      AND sl.exercise_id = :exerciseId
                )
                SELECT
                    session_id,
                    session_date,
                    session_completed_at,
                    workout_group_title,
                    exercise_id,
                    exercise_name,
                    top_set_weight_kg,
                    top_set_reps
                FROM ranked_sets
                WHERE rank_in_session = 1
                ORDER BY session_date ASC, session_completed_at ASC, session_id ASC
                """.trimIndent(),
            )
            .bind("userId", userId)
            .bind("exerciseId", exerciseId)
            .map { row, _ ->
                ExerciseProgressRow(
                    sessionId = row.get("session_id", UUID::class.java)!!,
                    sessionDate = row.get("session_date", LocalDate::class.java)!!,
                    workoutGroupTitle = row.get("workout_group_title", String::class.java)!!,
                    exerciseId = row.get("exercise_id", UUID::class.java)!!,
                    exerciseName = row.get("exercise_name", String::class.java)!!,
                    topSetWeightKg = row.get("top_set_weight_kg", BigDecimal::class.java)!!,
                    topSetReps =
                        requireNotNull(
                            row.get("top_set_reps", Integer::class.java)?.toInt(),
                        ) { "top_set_reps must not be null" },
                )
            }.all()
            .asFlow()
            .toList()
}

suspend fun selectExercisesBySetCount(
    databaseClient: DatabaseClient,
    userId: UUID,
    limit: Int,
    ascending: Boolean,
): List<TopExerciseRow> {
    val direction = if (ascending) "ASC" else "DESC"
    return databaseClient
        .sql(
            """
            SELECT sl.exercise_id, e.name AS exercise_name, COUNT(sl.id) AS set_count
            FROM set_logs sl
            JOIN workout_sessions ws ON sl.workout_session_id = ws.id
            JOIN exercises e ON sl.exercise_id = e.id AND e.user_id = :userId
            WHERE ws.user_id = :userId
            GROUP BY sl.exercise_id, e.name
            ORDER BY set_count $direction, e.name ASC
            LIMIT :limit
            """.trimIndent(),
        ).bind("userId", userId)
        .bind("limit", limit)
        .map { row, _ ->
            TopExerciseRow(
                exerciseId = row.get("exercise_id", UUID::class.java)!!,
                exerciseName = row.get("exercise_name", String::class.java)!!,
                setCount =
                    Math.toIntExact(
                        requireNotNull(row.get("set_count", java.lang.Long::class.java)).toLong(),
                    ),
            )
        }.all()
        .asFlow()
        .toList()
}
