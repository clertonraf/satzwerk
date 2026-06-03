package com.satzwerk.sessions

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class SessionHistoryRow(
    val id: UUID,
    val workoutGroupId: UUID,
    val workoutGroupTitle: String,
    val startedAt: Instant,
    val completedAt: Instant?,
    val notes: String?,
    val setCount: Int,
)

@Repository
class SessionQueryRepository(
    private val databaseClient: DatabaseClient,
) {
    suspend fun findHistoryWithDetails(userId: UUID): List<SessionHistoryRow> =
        databaseClient
            .sql(
                """
                SELECT
                    ws.id,
                    ws.workout_group_id,
                    wg.title AS workout_group_title,
                    ws.started_at,
                    ws.completed_at,
                    ws.notes,
                    COUNT(sl.id) AS set_count
                FROM workout_sessions ws
                JOIN workout_groups wg ON ws.workout_group_id = wg.id
                LEFT JOIN set_logs sl ON sl.workout_session_id = ws.id
                WHERE ws.user_id = :userId
                  AND ws.completed_at IS NOT NULL
                GROUP BY ws.id, wg.title
                ORDER BY ws.completed_at DESC
                """.trimIndent(),
            ).bind("userId", userId)
            .map { row, _ ->
                SessionHistoryRow(
                    id = row.get("id", UUID::class.java)!!,
                    workoutGroupId = row.get("workout_group_id", UUID::class.java)!!,
                    workoutGroupTitle = row.get("workout_group_title", String::class.java)!!,
                    startedAt = row.get("started_at", Instant::class.java)!!,
                    completedAt = row.get("completed_at", Instant::class.java),
                    notes = row.get("notes", String::class.java),
                    setCount = row.get("set_count", java.lang.Long::class.java)?.toInt() ?: 0,
                )
            }.all()
            .asFlow()
            .toList()

    suspend fun findMaxWeightForExercise(
        userId: UUID,
        exerciseId: UUID,
        beforeInstant: Instant,
    ): BigDecimal? =
        databaseClient
            .sql(
                """
                SELECT sl.weight AS max_weight
                FROM set_logs sl
                JOIN workout_sessions ws ON sl.workout_session_id = ws.id
                WHERE ws.user_id = :userId
                  AND sl.exercise_id = :exerciseId
                  AND sl.logged_at < :beforeInstant
                ORDER BY sl.weight DESC
                LIMIT 1
                """.trimIndent(),
            ).bind("userId", userId)
            .bind("exerciseId", exerciseId)
            .bind("beforeInstant", beforeInstant)
            .map { row, _ -> row.get("max_weight", BigDecimal::class.java) }
            .one()
            .awaitSingleOrNull()
}
