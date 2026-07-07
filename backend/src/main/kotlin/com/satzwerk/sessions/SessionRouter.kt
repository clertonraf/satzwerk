package com.satzwerk.sessions

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
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import org.springframework.web.reactive.function.server.buildAndAwait
import org.springframework.web.reactive.function.server.coRouter

@Configuration
class SessionRouter {
    @Bean
    fun sessionRoutes(
        workoutSessionService: WorkoutSessionService,
        validator: Validator,
    ) = coRouter {
        "/api/sessions".nest {
            sessionStartRoutes(workoutSessionService, validator)
            sessionSetLogRoutes(workoutSessionService, validator)
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
    validator: Validator,
) {
    POST("/{id}/set-logs") { request ->
        handleErrors(extra = mapOf(ConflictException::class to HttpStatus.CONFLICT)) {
            val ctx = RequestContext(request)
            val body = ctx.body<AddSetLogRequest>()
            validateOrBadRequest(validator, body) {
                val response = workoutSessionService.addSetLog(ctx.userId(), ctx.pathId("id"), body)
                ServerResponse.status(HttpStatus.CREATED).bodyValueAndAwait(response)
            }
        }
    }
    PATCH("/{id}/set-logs/{setLogId}") { request ->
        handleErrors(extra = mapOf(ConflictException::class to HttpStatus.CONFLICT)) {
            val ctx = RequestContext(request)
            val body = ctx.body<UpdateSetLogRequest>()
            validateOrBadRequest(validator, body) {
                val response =
                    workoutSessionService.updateSetLog(
                        ctx.userId(),
                        ctx.pathId("id"),
                        ctx.pathId("setLogId"),
                        body,
                    )
                ServerResponse.ok().bodyValueAndAwait(response)
            }
        }
    }
    DELETE("/{id}/set-logs/{setLogId}") { request ->
        handleErrors(extra = mapOf(ConflictException::class to HttpStatus.CONFLICT)) {
            val ctx = RequestContext(request)
            workoutSessionService.deleteSetLog(ctx.userId(), ctx.pathId("id"), ctx.pathId("setLogId"))
            ServerResponse.noContent().buildAndAwait()
        }
    }
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
