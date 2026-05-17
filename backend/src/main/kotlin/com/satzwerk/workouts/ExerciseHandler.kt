package com.satzwerk.workouts

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
class ExerciseHandler(
    private val exerciseService: ExerciseService,
    private val validator: Validator,
) {
    suspend fun create(request: ServerRequest): ServerResponse =
        handleErrors {
            val body = request.awaitBody<CreateExerciseRequest>()
            validateOrBadRequest(validator, body) {
                val response = exerciseService.create(currentUserId(request), body)
                ServerResponse.status(HttpStatus.CREATED).bodyValueAndAwait(response)
            }
        }

    suspend fun list(request: ServerRequest): ServerResponse =
        handleErrors {
            val response =
                exerciseService.list(
                    currentUserId(request),
                    request.queryParam("muscleGroup").orElse(null),
                )
            ServerResponse.ok().bodyValueAndAwait(response)
        }

    suspend fun getById(request: ServerRequest): ServerResponse =
        handleErrors {
            val response =
                exerciseService.getOwned(
                    currentUserId(request),
                    UUID.fromString(request.pathVariable("id")),
                )
            ServerResponse.ok().bodyValueAndAwait(response)
        }

    suspend fun update(request: ServerRequest): ServerResponse =
        handleErrors {
            val response =
                exerciseService.update(
                    currentUserId(request),
                    UUID.fromString(request.pathVariable("id")),
                    request.awaitBody<UpdateExerciseRequest>(),
                )
            ServerResponse.ok().bodyValueAndAwait(response)
        }

    suspend fun delete(request: ServerRequest): ServerResponse =
        handleErrors {
            exerciseService.delete(
                currentUserId(request),
                UUID.fromString(request.pathVariable("id")),
            )
            ServerResponse.noContent().buildAndAwait()
        }
}
