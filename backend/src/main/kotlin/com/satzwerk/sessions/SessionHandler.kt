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
import org.springframework.web.reactive.function.server.buildAndAwait
import java.util.UUID

@Component
class SessionHandler(
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

    suspend fun getOpen(request: ServerRequest): ServerResponse =
        handleErrors(withConflict = true) {
            val response = workoutSessionService.getOpen(currentUserId(request))
            ServerResponse.ok().bodyValueAndAwait(response)
        }

    suspend fun addSetLog(request: ServerRequest): ServerResponse =
        handleErrors(withConflict = true) {
            val body = request.awaitBody<AddSetLogRequest>()
            validateOrBadRequest(validator, body) {
                val response =
                    workoutSessionService.addSetLog(
                        currentUserId(request),
                        UUID.fromString(request.pathVariable("id")),
                        body,
                    )
                ServerResponse.status(HttpStatus.CREATED).bodyValueAndAwait(response)
            }
        }

    suspend fun updateSetLog(request: ServerRequest): ServerResponse =
        handleErrors(withConflict = true) {
            val body = request.awaitBody<UpdateSetLogRequest>()
            validateOrBadRequest(validator, body) {
                val response =
                    workoutSessionService.updateSetLog(
                        currentUserId(request),
                        UUID.fromString(request.pathVariable("id")),
                        UUID.fromString(request.pathVariable("setLogId")),
                        body,
                    )
                ServerResponse.ok().bodyValueAndAwait(response)
            }
        }

    suspend fun complete(request: ServerRequest): ServerResponse =
        handleErrors(withConflict = true) {
            val response =
                workoutSessionService.complete(
                    currentUserId(request),
                    UUID.fromString(request.pathVariable("id")),
                    request.awaitBody<CompleteSessionRequest>(),
                )
            ServerResponse.ok().bodyValueAndAwait(response)
        }

    suspend fun discard(request: ServerRequest): ServerResponse =
        handleErrors(withConflict = true) {
            workoutSessionService.discard(
                currentUserId(request),
                UUID.fromString(request.pathVariable("id")),
            )
            ServerResponse.noContent().buildAndAwait()
        }

    suspend fun history(request: ServerRequest): ServerResponse =
        handleErrors(withConflict = true) {
            val response = workoutSessionService.history(currentUserId(request))
            ServerResponse.ok().bodyValueAndAwait(response)
        }
}
