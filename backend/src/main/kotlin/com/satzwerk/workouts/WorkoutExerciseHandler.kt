package com.satzwerk.workouts

import com.satzwerk.common.body
import com.satzwerk.common.handleErrors
import com.satzwerk.common.validateOrBadRequest
import jakarta.validation.Validator
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import org.springframework.web.reactive.function.server.buildAndAwait

@Component
class WorkoutExerciseHandler(
    private val workoutExerciseService: WorkoutExerciseService,
    private val validator: Validator,
) {
    suspend fun create(request: ServerRequest): ServerResponse =
        handleErrors(request) { ctx ->
            val body = ctx.body<CreateWorkoutExerciseRequest>()
            validateOrBadRequest(validator, body) {
                val response =
                    workoutExerciseService.create(
                        ctx.userId(),
                        ctx.pathId("planId"),
                        ctx.pathId("groupId"),
                        body,
                    )
                ServerResponse.status(HttpStatus.CREATED).bodyValueAndAwait(response)
            }
        }

    suspend fun update(request: ServerRequest): ServerResponse =
        handleErrors(request) { ctx ->
            val body = ctx.body<UpdateWorkoutExerciseRequest>()
            validateOrBadRequest(validator, body) {
                val response =
                    workoutExerciseService.update(
                        ctx.userId(),
                        ctx.pathId("planId"),
                        ctx.pathId("groupId"),
                        ctx.pathId("exerciseId"),
                        body,
                    )
                ServerResponse.ok().bodyValueAndAwait(response)
            }
        }

    suspend fun delete(request: ServerRequest): ServerResponse =
        handleErrors(request) { ctx ->
            workoutExerciseService.delete(
                ctx.userId(),
                ctx.pathId("planId"),
                ctx.pathId("groupId"),
                ctx.pathId("exerciseId"),
            )
            ServerResponse.noContent().buildAndAwait()
        }

    suspend fun reorder(request: ServerRequest): ServerResponse =
        handleErrors(request) { ctx ->
            val body = ctx.body<ReorderRequest>()
            validateOrBadRequest(validator, body) {
                val response =
                    workoutExerciseService.reorder(
                        ctx.userId(),
                        ctx.pathId("planId"),
                        ctx.pathId("groupId"),
                        ctx.pathId("exerciseId"),
                        body.direction,
                    )
                ServerResponse.ok().bodyValueAndAwait(response)
            }
        }
}
