package com.satzwerk.sessions

import com.satzwerk.common.ConflictException
import com.satzwerk.common.NotFoundException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.buildAndAwait
import reactor.core.publisher.Mono
import java.security.Principal
import java.util.UUID

class SessionRouterTest {
    @Test
    fun `withOwnedOpenSession passes the validated WorkoutSession into the route block`() {
        val userId = UUID.randomUUID()
        val sessionId = UUID.randomUUID()
        val session = WorkoutSession(id = sessionId, userId = userId, workoutGroupId = UUID.randomUUID())
        val request = request(userId, sessionId)
        val workoutSessionService =
            mock<WorkoutSessionService> {
                onBlocking { requireOwnedOpenSession(eq(userId), eq(sessionId)) } doReturn session
            }

        var blockRan = false

        val response =
            runBlocking {
                withOwnedOpenSession(request, workoutSessionService) { ctx, validatedSession ->
                    blockRan = true
                    assertEquals(userId, ctx.userId())
                    assertEquals(sessionId, ctx.pathId("id"))
                    assertSame(session, validatedSession)
                    ServerResponse.noContent().buildAndAwait()
                }
            }

        assertTrue(blockRan)
        assertEquals(HttpStatus.NO_CONTENT, response.statusCode())
    }

    @Test
    fun `withOwnedOpenSession returns not found when ownership lookup fails`() {
        val userId = UUID.randomUUID()
        val sessionId = UUID.randomUUID()
        val request = request(userId, sessionId)
        val workoutSessionService =
            mock<WorkoutSessionService> {
                onBlocking { requireOwnedOpenSession(eq(userId), eq(sessionId)) } doAnswer {
                    throw NotFoundException("Workout session not found")
                }
            }

        var blockRan = false

        val response =
            runBlocking {
                withOwnedOpenSession(request, workoutSessionService) { _, _ ->
                    blockRan = true
                    ServerResponse.noContent().buildAndAwait()
                }
            }

        assertTrue(!blockRan)
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode())
    }

    @Test
    fun `withOwnedOpenSession returns conflict when session is already completed`() {
        val userId = UUID.randomUUID()
        val sessionId = UUID.randomUUID()
        val request = request(userId, sessionId)
        val workoutSessionService =
            mock<WorkoutSessionService> {
                onBlocking { requireOwnedOpenSession(eq(userId), eq(sessionId)) } doAnswer {
                    throw ConflictException("Workout session is already completed")
                }
            }

        var blockRan = false

        val response =
            runBlocking {
                withOwnedOpenSession(request, workoutSessionService) { _, _ ->
                    blockRan = true
                    ServerResponse.noContent().buildAndAwait()
                }
            }

        assertTrue(!blockRan)
        assertEquals(HttpStatus.CONFLICT, response.statusCode())
    }

    private fun request(
        userId: UUID,
        sessionId: UUID,
    ): ServerRequest {
        val principal = mock<Principal> { on { name } doReturn userId.toString() }
        return mock {
            on { principal() } doReturn Mono.just(principal)
            on { pathVariable("id") } doReturn sessionId.toString()
        }
    }
}
