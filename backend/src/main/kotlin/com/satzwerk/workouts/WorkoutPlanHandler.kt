package com.satzwerk.workouts

import com.satzwerk.common.ErrorResponse
import com.satzwerk.common.body
import com.satzwerk.common.handleErrors
import com.satzwerk.common.validateOrBadRequest
import jakarta.validation.Validator
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.http.HttpStatus
import org.springframework.http.codec.multipart.FilePart
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import org.springframework.web.reactive.function.server.buildAndAwait

@Component
class WorkoutPlanHandler(
    private val workoutPlanService: WorkoutPlanService,
    private val planImportService: PlanImportService,
    private val validator: Validator,
) {
    suspend fun create(request: ServerRequest): ServerResponse =
        handleErrors(
            request,
            extra =
                mapOf(
                    WebClientRequestException::class to HttpStatus.SERVICE_UNAVAILABLE,
                    WebClientResponseException::class to HttpStatus.SERVICE_UNAVAILABLE,
                ),
        ) { ctx ->
            val body = ctx.body<CreatePlanRequest>()
            validateOrBadRequest(validator, body) {
                val response = workoutPlanService.create(ctx.userId(), body)
                ServerResponse.status(HttpStatus.CREATED).bodyValueAndAwait(response)
            }
        }

    suspend fun list(request: ServerRequest): ServerResponse =
        handleErrors(
            request,
            extra =
                mapOf(
                    WebClientRequestException::class to HttpStatus.SERVICE_UNAVAILABLE,
                    WebClientResponseException::class to HttpStatus.SERVICE_UNAVAILABLE,
                ),
        ) { ctx ->
            ServerResponse.ok().bodyValueAndAwait(workoutPlanService.list(ctx.userId()))
        }

    suspend fun import(request: ServerRequest): ServerResponse =
        handleErrors(
            request,
            extra =
                mapOf(
                    WebClientRequestException::class to HttpStatus.SERVICE_UNAVAILABLE,
                    WebClientResponseException::class to HttpStatus.SERVICE_UNAVAILABLE,
                ),
        ) { ctx ->
            val multipartData = request.multipartData().awaitSingle()
            val filePart =
                multipartData.getFirst("file") as? FilePart
                    ?: return@handleErrors ServerResponse
                        .badRequest()
                        .bodyValueAndAwait(ErrorResponse("Missing 'file' part"))

            val response = planImportService.import(ctx.userId(), filePart)
            ServerResponse.status(HttpStatus.CREATED).bodyValueAndAwait(response)
        }

    suspend fun getDetail(request: ServerRequest): ServerResponse =
        handleErrors(
            request,
            extra =
                mapOf(
                    WebClientRequestException::class to HttpStatus.SERVICE_UNAVAILABLE,
                    WebClientResponseException::class to HttpStatus.SERVICE_UNAVAILABLE,
                ),
        ) { ctx ->
            val response =
                workoutPlanService.getDetail(
                    ctx.userId(),
                    ctx.pathId("planId"),
                )
            ServerResponse.ok().bodyValueAndAwait(response)
        }

    suspend fun update(request: ServerRequest): ServerResponse =
        handleErrors(
            request,
            extra =
                mapOf(
                    WebClientRequestException::class to HttpStatus.SERVICE_UNAVAILABLE,
                    WebClientResponseException::class to HttpStatus.SERVICE_UNAVAILABLE,
                ),
        ) { ctx ->
            val body = ctx.body<UpdatePlanRequest>()
            validateOrBadRequest(validator, body) {
                val response =
                    workoutPlanService.update(
                        ctx.userId(),
                        ctx.pathId("planId"),
                        body,
                    )
                ServerResponse.ok().bodyValueAndAwait(response)
            }
        }

    suspend fun delete(request: ServerRequest): ServerResponse =
        handleErrors(
            request,
            extra =
                mapOf(
                    WebClientRequestException::class to HttpStatus.SERVICE_UNAVAILABLE,
                    WebClientResponseException::class to HttpStatus.SERVICE_UNAVAILABLE,
                ),
        ) { ctx ->
            workoutPlanService.delete(
                ctx.userId(),
                ctx.pathId("planId"),
            )
            ServerResponse.noContent().buildAndAwait()
        }

    suspend fun activate(request: ServerRequest): ServerResponse =
        handleErrors(
            request,
            extra =
                mapOf(
                    WebClientRequestException::class to HttpStatus.SERVICE_UNAVAILABLE,
                    WebClientResponseException::class to HttpStatus.SERVICE_UNAVAILABLE,
                ),
        ) { ctx ->
            workoutPlanService.activate(
                ctx.userId(),
                ctx.pathId("planId"),
            )
            ServerResponse.noContent().buildAndAwait()
        }
}
