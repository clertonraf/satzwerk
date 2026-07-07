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
class ExerciseHandler(
    private val exerciseService: ExerciseService,
    private val validator: Validator,
) {
    suspend fun create(request: ServerRequest): ServerResponse =
        handleErrors(request) { ctx ->
            val body = ctx.body<CreateExerciseRequest>()
            validateOrBadRequest(validator, body) {
                val response = exerciseService.create(ctx.userId(), body)
                ServerResponse.status(HttpStatus.CREATED).bodyValueAndAwait(response)
            }
        }

    suspend fun list(request: ServerRequest): ServerResponse =
        handleErrors(request) { ctx ->
            val response =
                exerciseService.list(
                    ctx.userId(),
                    ctx.queryParam("muscleGroup"),
                )
            ServerResponse.ok().bodyValueAndAwait(response)
        }

    suspend fun getById(request: ServerRequest): ServerResponse =
        handleErrors(request) { ctx ->
            val response =
                exerciseService.getOwned(
                    ctx.userId(),
                    ctx.pathId("id"),
                )
            ServerResponse.ok().bodyValueAndAwait(response)
        }

    suspend fun update(request: ServerRequest): ServerResponse =
        handleErrors(request) { ctx ->
            val response =
                exerciseService.update(
                    ctx.userId(),
                    ctx.pathId("id"),
                    ctx.body<UpdateExerciseRequest>(),
                )
            ServerResponse.ok().bodyValueAndAwait(response)
        }

    suspend fun delete(request: ServerRequest): ServerResponse =
        handleErrors(request) { ctx ->
            exerciseService.delete(
                ctx.userId(),
                ctx.pathId("id"),
            )
            ServerResponse.noContent().buildAndAwait()
        }
}
