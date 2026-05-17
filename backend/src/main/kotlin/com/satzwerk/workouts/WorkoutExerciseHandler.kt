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
class WorkoutExerciseHandler(
    private val workoutExerciseService: WorkoutExerciseService,
    private val validator: Validator,
) {
    suspend fun create(request: ServerRequest): ServerResponse =
        handleErrors {
            val body = request.awaitBody<CreateWorkoutExerciseRequest>()
            validateOrBadRequest(validator, body) {
                val response =
                    workoutExerciseService.create(
                        currentUserId(request),
                        UUID.fromString(request.pathVariable("planId")),
                        UUID.fromString(request.pathVariable("groupId")),
                        body,
                    )
                ServerResponse.status(HttpStatus.CREATED).bodyValueAndAwait(response)
            }
        }

    suspend fun update(request: ServerRequest): ServerResponse =
        handleErrors {
            val body = request.awaitBody<UpdateWorkoutExerciseRequest>()
            validateOrBadRequest(validator, body) {
                val response =
                    workoutExerciseService.update(
                        currentUserId(request),
                        UUID.fromString(request.pathVariable("planId")),
                        UUID.fromString(request.pathVariable("groupId")),
                        UUID.fromString(request.pathVariable("exerciseId")),
                        body,
                    )
                ServerResponse.ok().bodyValueAndAwait(response)
            }
        }

    suspend fun delete(request: ServerRequest): ServerResponse =
        handleErrors {
            workoutExerciseService.delete(
                currentUserId(request),
                UUID.fromString(request.pathVariable("planId")),
                UUID.fromString(request.pathVariable("groupId")),
                UUID.fromString(request.pathVariable("exerciseId")),
            )
            ServerResponse.noContent().buildAndAwait()
        }

    suspend fun reorder(request: ServerRequest): ServerResponse =
        handleErrors {
            val body = request.awaitBody<ReorderRequest>()
            validateOrBadRequest(validator, body) {
                val response =
                    workoutExerciseService.reorder(
                        currentUserId(request),
                        UUID.fromString(request.pathVariable("planId")),
                        UUID.fromString(request.pathVariable("groupId")),
                        UUID.fromString(request.pathVariable("exerciseId")),
                        body.direction,
                    )
                ServerResponse.ok().bodyValueAndAwait(response)
            }
        }
}
