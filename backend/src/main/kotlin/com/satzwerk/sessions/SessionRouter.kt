package com.satzwerk.sessions

import com.fasterxml.jackson.databind.ObjectMapper
import com.satzwerk.common.ConflictException
import com.satzwerk.common.RequestContext
import com.satzwerk.common.body
import com.satzwerk.common.handleErrors
import com.satzwerk.common.validateOrBadRequest
import jakarta.validation.Validator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.server.CoRouterFunctionDsl
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import org.springframework.web.reactive.function.server.buildAndAwait
import org.springframework.web.reactive.function.server.coRouter

@Configuration
class SessionRouter {
    @Bean
    fun sessionRoutes(
        workoutSessionService: WorkoutSessionService,
        setLogService: SetLogService,
        validator: Validator,
        objectMapper: ObjectMapper,
    ) = coRouter {
        "/api/sessions".nest {
            sessionStartRoutes(workoutSessionService, validator)
            sessionSetLogRoutes(workoutSessionService, setLogService, validator, objectMapper)
            sessionLifecycleRoutes(workoutSessionService)
        }
    }
}

private fun CoRouterFunctionDsl.sessionStartRoutes(
    workoutSessionService: WorkoutSessionService,
    validator: Validator,
) {
    POST("") { request ->
        handleErrors(extra = mapOf(ConflictException::class to HttpStatus.CONFLICT)) {
            val ctx = RequestContext(request)
            val body = ctx.body<StartSessionRequest>()
            validateOrBadRequest(validator, body) {
                val response = workoutSessionService.start(ctx.userId(), body.workoutGroupId)
                ServerResponse.status(HttpStatus.CREATED).bodyValueAndAwait(response)
            }
        }
    }
    GET("/start-options") { request ->
        handleErrors {
            val ctx = RequestContext(request)
            ServerResponse.ok().bodyValueAndAwait(workoutSessionService.getStartOptions(ctx.userId()))
        }
    }
    GET("/open/plan-detail") { request ->
        handleErrors {
            val ctx = RequestContext(request)
            ServerResponse.ok().bodyValueAndAwait(workoutSessionService.getOpenPlanDetail(ctx.userId()))
        }
    }
    GET("/open") { request ->
        handleErrors(extra = mapOf(ConflictException::class to HttpStatus.CONFLICT)) {
            val ctx = RequestContext(request)
            ServerResponse.ok().bodyValueAndAwait(workoutSessionService.getOpen(ctx.userId()))
        }
    }
}

private fun CoRouterFunctionDsl.sessionSetLogRoutes(
    workoutSessionService: WorkoutSessionService,
    setLogService: SetLogService,
    validator: Validator,
    objectMapper: ObjectMapper,
) {
    POST("/{id}/set-logs") { request ->
        withOwnedOpenSession(request, workoutSessionService) { ctx, session ->
            val body = ctx.body<AddSetLogRequest>()
            validateOrBadRequest(validator, body) {
                ServerResponse.status(HttpStatus.CREATED).bodyValueAndAwait(setLogService.add(session, body))
            }
        }
    }
    PATCH("/{id}/set-logs/{setLogId}") { request ->
        withOwnedOpenSession(request, workoutSessionService) { ctx, session ->
            val body = parseUpdateSetLogRequest(ctx.body(), objectMapper)
            validateOrBadRequest(validator, body) {
                ServerResponse.ok().bodyValueAndAwait(
                    setLogService.update(session, ctx.pathId("setLogId"), body),
                )
            }
        }
    }
    DELETE("/{id}/set-logs/{setLogId}") { request ->
        withOwnedOpenSession(request, workoutSessionService) { ctx, session ->
            setLogService.delete(session, ctx.pathId("setLogId"))
            ServerResponse.noContent().buildAndAwait()
        }
    }
}

internal suspend fun withOwnedOpenSession(
    request: ServerRequest,
    workoutSessionService: WorkoutSessionService,
    block: suspend (RequestContext, WorkoutSession) -> ServerResponse,
): ServerResponse =
    handleErrors(request, extra = mapOf(ConflictException::class to HttpStatus.CONFLICT)) { ctx ->
        val session = workoutSessionService.requireOwnedOpenSession(ctx.userId(), ctx.pathId("id"))
        block(ctx, session)
    }

private fun CoRouterFunctionDsl.sessionLifecycleRoutes(workoutSessionService: WorkoutSessionService) {
    POST("/{id}/complete") { request ->
        handleErrors(extra = mapOf(ConflictException::class to HttpStatus.CONFLICT)) {
            val ctx = RequestContext(request)
            val response =
                workoutSessionService.complete(
                    ctx.userId(),
                    ctx.pathId("id"),
                    ctx.body<CompleteSessionRequest>(),
                )
            ServerResponse.ok().bodyValueAndAwait(response)
        }
    }
    DELETE("/{id}") { request ->
        handleErrors(extra = mapOf(ConflictException::class to HttpStatus.CONFLICT)) {
            val ctx = RequestContext(request)
            workoutSessionService.discard(ctx.userId(), ctx.pathId("id"))
            ServerResponse.noContent().buildAndAwait()
        }
    }
    GET("/history") { request ->
        handleErrors(extra = mapOf(ConflictException::class to HttpStatus.CONFLICT)) {
            val ctx = RequestContext(request)
            ServerResponse.ok().bodyValueAndAwait(workoutSessionService.history(ctx.userId()))
        }
    }
    GET("/{id}/reference-weights") { request ->
        handleErrors {
            val ctx = RequestContext(request)
            ServerResponse.ok().bodyValueAndAwait(
                workoutSessionService.getReferenceWeights(ctx.userId(), ctx.pathId("id")),
            )
        }
    }
    GET("/{id}") { request ->
        handleErrors {
            val ctx = RequestContext(request)
            ServerResponse.ok().bodyValueAndAwait(
                workoutSessionService.getById(ctx.userId(), ctx.pathId("id")),
            )
        }
    }
}
