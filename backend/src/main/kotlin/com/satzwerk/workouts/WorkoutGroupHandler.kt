package com.satzwerk.workouts

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
class WorkoutGroupHandler(
    private val workoutGroupService: WorkoutGroupService,
    private val validator: Validator,
) {
    suspend fun create(request: ServerRequest): ServerResponse =
        handleWorkoutErrors {
            val body = request.awaitBody<CreateGroupRequest>()
            validate(validator, body)?.let {
                return@handleWorkoutErrors ServerResponse.badRequest().bodyValueAndAwait(it)
            }

            val response =
                workoutGroupService.create(
                    currentUserId(request),
                    UUID.fromString(request.pathVariable("planId")),
                    body,
                )
            ServerResponse.status(HttpStatus.CREATED).bodyValueAndAwait(response)
        }

    suspend fun update(request: ServerRequest): ServerResponse =
        handleWorkoutErrors {
            val body = request.awaitBody<UpdateGroupRequest>()
            validate(validator, body)?.let {
                return@handleWorkoutErrors ServerResponse.badRequest().bodyValueAndAwait(it)
            }

            val response =
                workoutGroupService.update(
                    currentUserId(request),
                    UUID.fromString(request.pathVariable("planId")),
                    UUID.fromString(request.pathVariable("groupId")),
                    body,
                )
            ServerResponse.ok().bodyValueAndAwait(response)
        }

    suspend fun delete(request: ServerRequest): ServerResponse =
        handleWorkoutErrors {
            workoutGroupService.delete(
                currentUserId(request),
                UUID.fromString(request.pathVariable("planId")),
                UUID.fromString(request.pathVariable("groupId")),
            )
            ServerResponse.noContent().buildAndAwait()
        }
}
