package com.satzwerk.sessions

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.math.RoundingMode
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

private data class PreviousWeightRow(
    val exerciseId: UUID,
    val previousWeight: BigDecimal?,
)

private data class PersonalRecordRow(
    val exerciseId: UUID,
    val prWeight: BigDecimal?,
    val prReps: Int?,
)

private const val EPLEY_DIVISOR = "30"
private const val EPLEY_DIVISION_SCALE = 10

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

    suspend fun findReferenceWeights(
        userId: UUID,
        exerciseIds: List<UUID>,
        currentSessionId: UUID,
    ): List<ExerciseReferenceWeights> {
        if (exerciseIds.isEmpty()) {
            return emptyList()
        }

        val previousWeights = findPreviousWeights(userId, exerciseIds, currentSessionId)
        val personalRecords = findPersonalRecords(userId, exerciseIds)

        return exerciseIds.map { exerciseId ->
            val previousWeight = previousWeights[exerciseId]?.previousWeight
            val personalRecord = personalRecords[exerciseId]
            ExerciseReferenceWeights(
                exerciseId = exerciseId,
                previousWeightKg = previousWeight,
                prWeightKg = personalRecord?.prWeight,
                estimatedOneRepMaxKg = personalRecord.toEstimatedOneRepMaxKg(),
            )
        }
    }

    private suspend fun findPreviousWeights(
        userId: UUID,
        exerciseIds: List<UUID>,
        currentSessionId: UUID,
    ): Map<UUID, PreviousWeightRow> =
        databaseClient
            .sql(
                """
                SELECT DISTINCT ON (sl.exercise_id)
                    sl.exercise_id,
                    sl.weight AS previous_weight
                FROM set_logs sl
                JOIN workout_sessions ws ON sl.workout_session_id = ws.id
                WHERE ws.user_id = :userId
                  AND sl.exercise_id = ANY(:exerciseIds)
                  AND ws.completed_at IS NOT NULL
                  AND ws.id <> :currentSessionId
                ORDER BY sl.exercise_id, sl.logged_at DESC, sl.id DESC
                """.trimIndent(),
            ).bind("userId", userId)
            .bind("exerciseIds", exerciseIds.toTypedArray())
            .bind("currentSessionId", currentSessionId)
            .map { row, _ ->
                PreviousWeightRow(
                    exerciseId = row.get("exercise_id", UUID::class.java)!!,
                    previousWeight = row.get("previous_weight", BigDecimal::class.java),
                )
            }.all()
            .asFlow()
            .toList()
            .associateBy(PreviousWeightRow::exerciseId)

    private suspend fun findPersonalRecords(
        userId: UUID,
        exerciseIds: List<UUID>,
    ): Map<UUID, PersonalRecordRow> =
        databaseClient
            .sql(
                """
                SELECT DISTINCT ON (sl.exercise_id)
                    sl.exercise_id,
                    sl.weight AS pr_weight,
                    sl.reps AS pr_reps
                FROM set_logs sl
                JOIN workout_sessions ws ON sl.workout_session_id = ws.id
                WHERE ws.user_id = :userId
                  AND sl.exercise_id = ANY(:exerciseIds)
                ORDER BY sl.exercise_id, sl.weight DESC, sl.logged_at DESC, sl.id DESC
                """.trimIndent(),
            ).bind("userId", userId)
            .bind("exerciseIds", exerciseIds.toTypedArray())
            .map { row, _ ->
                PersonalRecordRow(
                    exerciseId = row.get("exercise_id", UUID::class.java)!!,
                    prWeight = row.get("pr_weight", BigDecimal::class.java),
                    prReps = row.get("pr_reps", java.lang.Integer::class.java)?.toInt(),
                )
            }.all()
            .asFlow()
            .toList()
            .associateBy(PersonalRecordRow::exerciseId)

    suspend fun findMaxRatioForExercise(
        userId: UUID,
        exerciseId: UUID,
        beforeInstant: Instant,
        currentId: UUID? = null,
    ): BigDecimal? {
        val sql =
            if (currentId == null) {
                """
                SELECT ROUND(sl.weight / sl.reps, 10) AS max_ratio
                FROM set_logs sl
                JOIN workout_sessions ws ON sl.workout_session_id = ws.id
                WHERE ws.user_id = :userId
                  AND sl.exercise_id = :exerciseId
                  AND sl.logged_at <= :beforeInstant
                  AND sl.reps > 0
                ORDER BY max_ratio DESC, sl.logged_at DESC, sl.id DESC
                LIMIT 1
                """.trimIndent()
            } else {
                """
                SELECT ROUND(sl.weight / sl.reps, 10) AS max_ratio
                FROM set_logs sl
                JOIN workout_sessions ws ON sl.workout_session_id = ws.id
                WHERE ws.user_id = :userId
                  AND sl.exercise_id = :exerciseId
                  AND (sl.logged_at < :beforeInstant
                       OR (sl.logged_at = :beforeInstant AND sl.id < :currentId))
                  AND sl.reps > 0
                ORDER BY max_ratio DESC, sl.logged_at DESC, sl.id DESC
                LIMIT 1
                """.trimIndent()
            }
        var spec =
            databaseClient.sql(sql)
                .bind("userId", userId)
                .bind("exerciseId", exerciseId)
                .bind("beforeInstant", beforeInstant)
        if (currentId != null) spec = spec.bind("currentId", currentId)
        return spec
            .map { row, _ -> row.get("max_ratio", BigDecimal::class.java) }
            .one()
            .awaitSingleOrNull()
    }

    private fun PersonalRecordRow?.toEstimatedOneRepMaxKg(): BigDecimal? {
        if (this?.prWeight == null || prReps == null) {
            return null
        }
        return epley(prWeight, prReps)
    }

    private fun epley(
        weight: BigDecimal,
        reps: Int,
    ): BigDecimal =
        weight.multiply(
            BigDecimal.ONE.add(
                reps.toBigDecimal().divide(
                    BigDecimal(EPLEY_DIVISOR),
                    EPLEY_DIVISION_SCALE,
                    RoundingMode.HALF_UP,
                ),
            ),
        ).setScale(2, RoundingMode.HALF_UP)
}
