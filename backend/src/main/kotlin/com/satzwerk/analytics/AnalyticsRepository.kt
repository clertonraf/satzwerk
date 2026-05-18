package com.satzwerk.analytics

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.util.UUID

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
            .collectList()
            .awaitSingle()
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
            .collectList()
            .awaitSingle()
}
