package com.satzwerk.sessions

import com.satzwerk.auth.InsufficientScopeException
import com.satzwerk.auth.TokenScope
import com.satzwerk.common.RequestContext
import com.satzwerk.common.handleErrors
import com.satzwerk.common.requireScope
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import org.springframework.web.reactive.function.server.coRouter
import kotlin.reflect.KClass

private val scopeErrors: Map<KClass<out Throwable>, HttpStatus> =
    mapOf(InsufficientScopeException::class to HttpStatus.FORBIDDEN)

/**
 * Public session read surfaces under `/api/public/sessions`.
 * Accepted credentials: personal API tokens (#204) and partner app tokens (#205).
 * All endpoints require [TokenScope.SESSIONS_READ].
 *
 * Only completed session history and individual session detail are exposed — the open
 * (in-progress) session, start options, and reference weights are internal-only surfaces.
 */
@Configuration
class PublicSessionsRouter {
    @Bean
    fun publicSessionRoutes(workoutSessionService: WorkoutSessionService) =
        coRouter {
            "/api/public/sessions".nest {
                GET("/history") { request ->
                    handleErrors(extra = scopeErrors) {
                        requireScope(request, TokenScope.SESSIONS_READ)
                        val ctx = RequestContext(request)
                        ServerResponse.ok().bodyValueAndAwait(workoutSessionService.history(ctx.userId()))
                    }
                }
                GET("/{id}") { request ->
                    handleErrors(extra = scopeErrors) {
                        requireScope(request, TokenScope.SESSIONS_READ)
                        val ctx = RequestContext(request)
                        ServerResponse.ok().bodyValueAndAwait(
                            workoutSessionService.getById(ctx.userId(), ctx.pathId("id")),
                        )
                    }
                }
            }
        }
}
