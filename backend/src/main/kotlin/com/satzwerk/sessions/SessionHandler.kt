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
import org.springframework.web.reactive.function.server.buildAndAwait

@Component
class SessionHandler(
    private val workoutSessionService: WorkoutSessionService,
    private val setLogService: SetLogService,
    private val validator: Validator,
) {
    suspend fun getOpen(request: ServerRequest): ServerResponse =
        handleErrors(request, extra = mapOf(ConflictException::class to HttpStatus.CONFLICT)) { ctx ->
            val response = workoutSessionService.getOpen(ctx.userId())
            ServerResponse.ok().bodyValueAndAwait(response)
        }

    suspend fun addSetLog(request: ServerRequest): ServerResponse =
        handleErrors(request, extra = mapOf(ConflictException::class to HttpStatus.CONFLICT)) { ctx ->
            val body = ctx.body<AddSetLogRequest>()
            validateOrBadRequest(validator, body) {
                val session = workoutSessionService.requireOwnedOpenSession(ctx.userId(), ctx.pathId("id"))
                ServerResponse.status(HttpStatus.CREATED).bodyValueAndAwait(setLogService.add(session, body))
            }
        }

    suspend fun updateSetLog(request: ServerRequest): ServerResponse =
        handleErrors(request, extra = mapOf(ConflictException::class to HttpStatus.CONFLICT)) { ctx ->
            val body = ctx.body<UpdateSetLogRequest>()
            validateOrBadRequest(validator, body) {
                val session = workoutSessionService.requireOwnedOpenSession(ctx.userId(), ctx.pathId("id"))
                ServerResponse.ok().bodyValueAndAwait(
                    setLogService.update(session, ctx.pathId("setLogId"), body),
                )
            }
        }

    suspend fun deleteSetLog(request: ServerRequest): ServerResponse =
        handleErrors(request, extra = mapOf(ConflictException::class to HttpStatus.CONFLICT)) { ctx ->
            val session = workoutSessionService.requireOwnedOpenSession(ctx.userId(), ctx.pathId("id"))
            setLogService.delete(session, ctx.pathId("setLogId"))
            ServerResponse.noContent().buildAndAwait()
        }

    suspend fun complete(request: ServerRequest): ServerResponse =
        handleErrors(request, extra = mapOf(ConflictException::class to HttpStatus.CONFLICT)) { ctx ->
            val response =
                workoutSessionService.complete(
                    ctx.userId(),
                    ctx.pathId("id"),
                    ctx.body<CompleteSessionRequest>(),
                )
            ServerResponse.ok().bodyValueAndAwait(response)
        }

    suspend fun discard(request: ServerRequest): ServerResponse =
        handleErrors(request, extra = mapOf(ConflictException::class to HttpStatus.CONFLICT)) { ctx ->
            workoutSessionService.discard(ctx.userId(), ctx.pathId("id"))
            ServerResponse.noContent().buildAndAwait()
        }

    suspend fun start(request: ServerRequest): ServerResponse =
        handleErrors(request, extra = mapOf(ConflictException::class to HttpStatus.CONFLICT)) { ctx ->
            val response = workoutSessionService.start(ctx.userId(), ctx.pathId("id"))
            ServerResponse.status(HttpStatus.CREATED).bodyValueAndAwait(response)
        }

    suspend fun history(request: ServerRequest): ServerResponse =
        handleErrors(request) { ctx ->
            val response = workoutSessionService.history(ctx.userId())
            ServerResponse.ok().bodyValueAndAwait(response)
        }

    suspend fun getById(request: ServerRequest): ServerResponse =
        handleErrors(request) { ctx ->
            val response =
                workoutSessionService.getById(
                    ctx.userId(),
                    ctx.pathId("id"),
                )
            ServerResponse.ok().bodyValueAndAwait(response)
        }

    suspend fun getReferenceWeights(request: ServerRequest): ServerResponse =
        handleErrors(request) { ctx ->
            val response =
                workoutSessionService.getReferenceWeights(
                    ctx.userId(),
                    ctx.pathId("id"),
                )
            ServerResponse.ok().bodyValueAndAwait(response)
        }
}
