package com.satzwerk.sessions

import com.satzwerk.common.TransactionRunner
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
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

    private val inlineTransactionRunner =
        object : TransactionRunner {
            override suspend fun <T> required(block: suspend () -> T): T = block()

            override suspend fun afterCommit(block: suspend () -> Unit) {
                runCatching { block() }
            }
        }

    private fun service(): Triple<SetLogService, SetLogRepository, SetLogWriteRepository> {
        val queryRepo =
            mock<SessionQueryRepository> {
                onBlocking { findMaxRatioForExercise(any(), any(), any(), anyOrNull()) } doReturn null
            }
        val setLogRepo =
            mock<SetLogRepository> {
                onBlocking { save(any()) } doAnswer { invocation ->
                    val log = invocation.getArgument<SetLog>(0)
                    log.copy(id = UUID.randomUUID())
                }
            }
        val setLogWriteRepo =
            mock<SetLogWriteRepository> {
                onBlocking { insertWithCalculatedPr(any(), any()) } doAnswer { invocation ->
                    val log = invocation.getArgument<SetLog>(1)
                    log.copy(id = UUID.randomUUID(), isPr = true)
                }
            }
        return Triple(
            SetLogService(setLogRepo, setLogWriteRepo, queryRepo, mock(), inlineTransactionRunner),
            setLogRepo,
            setLogWriteRepo,
        )
    }

    @Test
    fun `add avoids the legacy read then save path`(): Unit =
        runBlocking {
            val queryRepo =
                mock<SessionQueryRepository> {
                    onBlocking { findMaxRatioForExercise(any(), any(), any(), anyOrNull()) } doReturn null
                }
            val setLogRepo =
                mock<SetLogRepository> {
                    onBlocking { save(any()) } doAnswer { invocation ->
                        val log = invocation.getArgument<SetLog>(0)
                        log.copy(id = UUID.randomUUID())
                    }
                }
            val setLogWriteRepo =
                mock<SetLogWriteRepository> {
                    onBlocking { insertWithCalculatedPr(any(), any()) } doAnswer { invocation ->
                        val log = invocation.getArgument<SetLog>(1)
                        log.copy(id = UUID.randomUUID(), isPr = true)
                    }
                }
            val service = SetLogService(setLogRepo, setLogWriteRepo, queryRepo, mock(), inlineTransactionRunner)
            val request = AddSetLogRequest(exerciseId = exerciseId, setNumber = 1, weight = BigDecimal("80"), reps = 5)

            val response = service.add(session, request)

            verify(queryRepo, never()).findMaxRatioForExercise(any(), any(), any(), anyOrNull())
            verify(setLogRepo, never()).save(any())
            verify(setLogWriteRepo).insertWithCalculatedPr(any(), any())
            assertEquals(exerciseId, response.exerciseId)
        }

    @Test
    fun `add delegates persistence to single-round-trip writer`(): Unit =
        runBlocking {
            val (svc, legacyRepo, writeRepo) = service()
            val request = AddSetLogRequest(exerciseId = exerciseId, setNumber = 1, weight = BigDecimal("80"), reps = 5)

            svc.add(session, request)

            val captor = argumentCaptor<SetLog>()
            verify(writeRepo).insertWithCalculatedPr(any(), captor.capture())
            verify(legacyRepo, never()).save(any())
            assertEquals(sessionId, captor.firstValue.workoutSessionId)
            assertEquals(exerciseId, captor.firstValue.exerciseId)
        }

    @Test
    fun `clear set logs invalidates analytics cache for session user`(): Unit =
        runBlocking {
            val analyticsCache = mock<com.satzwerk.analytics.AnalyticsReadCache>()
            val queryRepo =
                mock<SessionQueryRepository> {
                    onBlocking { findMaxRatioForExercise(any(), any(), any(), anyOrNull()) } doReturn null
                }
            val setLogRepo = mock<SetLogRepository>()
            val service = SetLogService(setLogRepo, mock(), queryRepo, analyticsCache, inlineTransactionRunner)

            service.clearSetLogs(session)

            verify(setLogRepo).deleteAllByWorkoutSessionId(sessionId)
            verify(analyticsCache).invalidateUser(userId)
            verify(analyticsCache, never()).invalidateUser(exerciseId)
        }

    @Test
    fun `add still returns success when post-commit cache invalidation fails`(): Unit =
        runBlocking {
            val queryRepo =
                mock<SessionQueryRepository> {
                    onBlocking { findMaxRatioForExercise(any(), any(), any(), anyOrNull()) } doReturn null
                }
            val setLogRepo =
                mock<SetLogRepository> {
                    onBlocking { save(any()) } doAnswer { invocation ->
                        val log = invocation.getArgument<SetLog>(0)
                        log.copy(id = UUID.randomUUID())
                    }
                }
            val analyticsCache =
                mock<com.satzwerk.analytics.AnalyticsReadCache> {
                    onBlocking { invalidateUser(userId) } doAnswer {
                        throw IllegalStateException("redis down")
                    }
                }
            val setLogWriteRepo =
                mock<SetLogWriteRepository> {
                    onBlocking { insertWithCalculatedPr(any(), any()) } doAnswer { invocation ->
                        val log = invocation.getArgument<SetLog>(1)
                        log.copy(id = UUID.randomUUID(), isPr = true)
                    }
                }
            val service = SetLogService(setLogRepo, setLogWriteRepo, queryRepo, analyticsCache, inlineTransactionRunner)
            val request = AddSetLogRequest(exerciseId = exerciseId, setNumber = 1, weight = BigDecimal("80"), reps = 5)

            val response = service.add(session, request)

            assertTrue(response.id.toString().isNotBlank())
            assertEquals(exerciseId, response.exerciseId)
            assertEquals(1, response.setNumber)
            verify(setLogWriteRepo).insertWithCalculatedPr(any(), any())
            verify(analyticsCache).invalidateUser(userId)
        }
}
