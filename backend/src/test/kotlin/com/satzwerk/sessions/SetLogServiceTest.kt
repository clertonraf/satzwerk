package com.satzwerk.sessions

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.math.BigDecimal
import java.util.UUID

class SetLogServiceTest {
    private val sessionId = UUID.randomUUID()
    private val userId = UUID.randomUUID()
    private val exerciseId = UUID.randomUUID()

    private val session =
        WorkoutSession(
            id = sessionId,
            userId = userId,
            workoutGroupId = UUID.randomUUID(),
        )

    private fun service(prevMaxRatio: BigDecimal?): Pair<SetLogService, SetLogRepository> {
        val queryRepo =
            mock<SessionQueryRepository> {
                onBlocking { findMaxRatioForExercise(any(), any(), any(), anyOrNull()) } doReturn prevMaxRatio
            }
        val setLogRepo =
            mock<SetLogRepository> {
                onBlocking { save(any()) } doAnswer { invocation ->
                    val log = invocation.getArgument<SetLog>(0)
                    log.copy(id = UUID.randomUUID())
                }
            }
        return SetLogService(setLogRepo, queryRepo) to setLogRepo
    }

    @Test
    fun `add sets isPr true when no prior record exists`(): Unit =
        runBlocking {
            val (svc, repo) = service(prevMaxRatio = null)
            val request = AddSetLogRequest(exerciseId = exerciseId, setNumber = 1, weight = BigDecimal("80"), reps = 5)

            svc.add(session, request)

            val captor = argumentCaptor<SetLog>()
            verify(repo).save(captor.capture())
            assertTrue(captor.firstValue.isPr)
        }

    @Test
    fun `add sets isPr false when ratio is below existing record`(): Unit =
        runBlocking {
            // prev = 80/5 = 16.0; new = 70/5 = 14.0 → not a PR
            val (svc, repo) = service(prevMaxRatio = BigDecimal("16.0000000000"))
            val request = AddSetLogRequest(exerciseId = exerciseId, setNumber = 1, weight = BigDecimal("70"), reps = 5)

            svc.add(session, request)

            val captor = argumentCaptor<SetLog>()
            verify(repo).save(captor.capture())
            assertFalse(captor.firstValue.isPr)
        }
}
