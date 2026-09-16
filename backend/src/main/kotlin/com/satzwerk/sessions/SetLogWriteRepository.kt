package com.satzwerk.sessions

import io.r2dbc.spi.Row
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import java.lang.Integer
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

private val INSERT_SET_LOG_WITH_PR_SQL =
    """
    INSERT INTO set_logs (
        workout_session_id,
        exercise_id,
        set_number,
        weight,
        reps,
        logged_at,
        is_pr,
        rir
    )
    SELECT
        :workoutSessionId,
        :exerciseId,
        :setNumber,
        :weight,
        :reps,
        :loggedAt,
        CASE
            WHEN :currentRatio IS NULL THEN FALSE
            WHEN prev.max_ratio IS NULL THEN TRUE
            ELSE :currentRatio > prev.max_ratio
        END,
        :rir
    FROM (
        SELECT MAX(ROUND(sl.weight / sl.reps, 10)) AS max_ratio
        FROM set_logs sl
        JOIN workout_sessions ws ON sl.workout_session_id = ws.id
        WHERE ws.user_id = :userId
          AND sl.exercise_id = :exerciseId
          AND sl.logged_at <= :loggedAt
          AND sl.reps > 0
    ) prev
    RETURNING id, workout_session_id, exercise_id, set_number, weight, reps, logged_at, is_pr, rir
    """.trimIndent()

@Repository
class SetLogWriteRepository(
    private val databaseClient: DatabaseClient,
) {
    suspend fun insertWithCalculatedPr(
        userId: UUID,
        setLog: SetLog,
    ): SetLog =
        buildInsertSpec(userId, setLog)
            .map { row, _ -> row.toSetLog() }
            .one()
            .awaitSingle()

    private fun buildInsertSpec(
        userId: UUID,
        setLog: SetLog,
    ): DatabaseClient.GenericExecuteSpec =
        databaseClient.sql(INSERT_SET_LOG_WITH_PR_SQL)
            .bind("userId", userId)
            .bind("workoutSessionId", setLog.workoutSessionId)
            .bind("exerciseId", setLog.exerciseId)
            .bind("setNumber", setLog.setNumber)
            .bind("weight", setLog.weight)
            .bind("reps", setLog.reps)
            .bind("loggedAt", setLog.loggedAt)
            .bindNullable("currentRatio", calculatePrRatio(setLog.weight, setLog.reps), BigDecimal::class.java)
            .bindNullable("rir", setLog.rir, Integer::class.java)
}

private fun DatabaseClient.GenericExecuteSpec.bindNullable(
    name: String,
    value: Any?,
    type: Class<*>,
): DatabaseClient.GenericExecuteSpec =
    if (value == null) {
        bindNull(name, type)
    } else {
        bind(name, value)
    }

private fun Row.toSetLog(): SetLog =
    SetLog(
        id = get("id", UUID::class.java),
        workoutSessionId = get("workout_session_id", UUID::class.java)!!,
        exerciseId = get("exercise_id", UUID::class.java)!!,
        setNumber = get("set_number", java.lang.Integer::class.java)!!.toInt(),
        weight = get("weight", BigDecimal::class.java)!!,
        reps = get("reps", java.lang.Integer::class.java)!!.toInt(),
        loggedAt = get("logged_at", Instant::class.java)!!,
        isPr = get("is_pr", java.lang.Boolean::class.java)!!.booleanValue(),
        rir = get("rir", java.lang.Integer::class.java)?.toInt(),
    )
