package com.satzwerk.sessions

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.time.Instant
import java.util.UUID

class WorkoutSessionServiceHistoryTest {
    private val userId = UUID.randomUUID()

    private val historyRow =
        SessionHistoryRow(
            id = UUID.randomUUID(),
            workoutGroupId = UUID.randomUUID(),
            workoutGroupTitle = "Push A",
            startedAt = Instant.parse("2024-01-10T10:00:00Z"),
            completedAt = Instant.parse("2024-01-10T11:00:00Z"),
            notes = null,
            setCount = 12,
        )

    @Test
    fun `history returns mapped WorkoutSessionResponse list from repository`(): Unit =
        runBlocking {
            val queryRepo =
                mock<SessionQueryRepository> {
                    onBlocking { findHistoryWithDetails(any()) } doReturn listOf(historyRow)
                }
            val service =
                WorkoutSessionService(
                    workoutSessionRepository = mock(),
                    workoutGroupRepository = mock(),
                    workoutPlanService = mock(),
                    personalRecordService = mock(),
                    setLogService = mock(),
                    sessionQueryRepository = queryRepo,
                )

            val result = service.history(userId)

            assertEquals(1, result.size)
            assertEquals(historyRow.id, result[0].id)
            assertEquals(historyRow.workoutGroupId, result[0].workoutGroupId)
            assertEquals(historyRow.workoutGroupTitle, result[0].workoutGroupTitle)
            assertEquals(historyRow.startedAt, result[0].startedAt)
            assertEquals(historyRow.completedAt, result[0].completedAt)
            assertEquals(historyRow.setCount, result[0].setCount)
            assertEquals(emptyList<Any>(), result[0].setLogs)
        }
}
