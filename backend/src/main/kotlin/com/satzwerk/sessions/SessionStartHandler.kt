package com.satzwerk.sessions

import com.satzwerk.common.currentUserId
import com.satzwerk.common.handleErrors
import com.satzwerk.common.validateOrBadRequest
import jakarta.validation.Validator
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.awaitBody
import org.springframework.web.reactive.function.server.bodyValueAndAwait

@Component
class SessionStartHandler(
    private val workoutSessionService: WorkoutSessionService,
    private val validator: Validator,
) {
    suspend fun start(request: ServerRequest): ServerResponse =
        handleErrors(withConflict = true) {
            val body = request.awaitBody<StartSessionRequest>()
            validateOrBadRequest(validator, body) {
                val response = workoutSessionService.start(currentUserId(request), body.workoutGroupId)
                ServerResponse.status(HttpStatus.CREATED).bodyValueAndAwait(response)
            }
        }

    suspend fun getStartOptions(request: ServerRequest): ServerResponse =
        handleErrors {
            val response = workoutSessionService.getStartOptions(currentUserId(request))
            ServerResponse.ok().bodyValueAndAwait(response)
        }

    suspend fun getOpenPlanDetail(request: ServerRequest): ServerResponse =
        handleErrors {
            val response = workoutSessionService.getOpenPlanDetail(currentUserId(request))
            ServerResponse.ok().bodyValueAndAwait(response)
        }
}
