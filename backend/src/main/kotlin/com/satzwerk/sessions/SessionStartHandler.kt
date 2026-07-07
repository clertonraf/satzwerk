package com.satzwerk.sessions

import com.satzwerk.common.ConflictException
import com.satzwerk.common.body
import com.satzwerk.common.handleErrors
import com.satzwerk.common.validateOrBadRequest
import jakarta.validation.Validator
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait

@Component
class SessionStartHandler(
    private val workoutSessionService: WorkoutSessionService,
    private val validator: Validator,
) {
    suspend fun start(request: ServerRequest): ServerResponse =
        handleErrors(request, extra = mapOf(ConflictException::class to HttpStatus.CONFLICT)) { ctx ->
            val body = ctx.body<StartSessionRequest>()
            validateOrBadRequest(validator, body) {
                val response = workoutSessionService.start(ctx.userId(), body.workoutGroupId)
                ServerResponse.status(HttpStatus.CREATED).bodyValueAndAwait(response)
            }
        }

    suspend fun getStartOptions(request: ServerRequest): ServerResponse =
        handleErrors(request) { ctx ->
            val response = workoutSessionService.getStartOptions(ctx.userId())
            ServerResponse.ok().bodyValueAndAwait(response)
        }

    suspend fun getOpenPlanDetail(request: ServerRequest): ServerResponse =
        handleErrors(request) { ctx ->
            val response = workoutSessionService.getOpenPlanDetail(ctx.userId())
            ServerResponse.ok().bodyValueAndAwait(response)
        }
}
