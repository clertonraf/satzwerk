package com.satzwerk.workouts

import com.satzwerk.common.ErrorResponse
import com.satzwerk.common.ForbiddenException
import com.satzwerk.common.NotFoundException
import com.satzwerk.common.ValidationErrorResponse
import jakarta.validation.Validator
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.awaitBody
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import org.springframework.web.reactive.function.server.buildAndAwait
import java.util.UUID

private typealias ResponseBlock = suspend () -> ServerResponse

@Component
class ExerciseHandler(
    private val exerciseService: ExerciseService,
    private val validator: Validator,
) {
    suspend fun create(request: ServerRequest): ServerResponse =
        handleErrors {
            val body = request.awaitBody<CreateExerciseRequest>()
            val violations = validator.validate(body)
            if (violations.isNotEmpty()) {
                return@handleErrors ServerResponse
                    .badRequest()
                    .bodyValueAndAwait(
                        ValidationErrorResponse(
                            violations.associate { it.propertyPath.toString() to it.message },
                        ),
                    )
            }

            val userId = currentUserId(request)
            val response = exerciseService.create(userId, body)
            ServerResponse.status(HttpStatus.CREATED).bodyValueAndAwait(response)
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

    private suspend fun currentUserId(request: ServerRequest): UUID {
        val principal = request.principal().awaitSingle()
        return UUID.fromString(principal.name)
    }

    private suspend fun handleErrors(block: ResponseBlock): ServerResponse =
        try {
            block()
        } catch (_: ForbiddenException) {
            ServerResponse.status(HttpStatus.FORBIDDEN).bodyValueAndAwait(ErrorResponse("Forbidden"))
        } catch (_: NotFoundException) {
            ServerResponse.status(HttpStatus.NOT_FOUND).bodyValueAndAwait(ErrorResponse("Not found"))
        }
}
