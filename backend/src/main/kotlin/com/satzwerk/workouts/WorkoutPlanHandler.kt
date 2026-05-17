package com.satzwerk.workouts

import com.satzwerk.common.ErrorResponse
import com.satzwerk.common.currentUserId
import com.satzwerk.common.handleErrors
import com.satzwerk.common.validateOrBadRequest
import jakarta.validation.Validator
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.http.HttpStatus
import org.springframework.http.codec.multipart.FilePart
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.awaitBody
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import org.springframework.web.reactive.function.server.buildAndAwait
import java.util.UUID

@Component
class WorkoutPlanHandler(
    private val workoutPlanService: WorkoutPlanService,
    private val planImportService: PlanImportService,
    private val validator: Validator,
) {
    suspend fun create(request: ServerRequest): ServerResponse =
        handleErrors(withWebClient = true) {
            val body = request.awaitBody<CreatePlanRequest>()
            validateOrBadRequest(validator, body) {
                val response = workoutPlanService.create(currentUserId(request), body)
                ServerResponse.status(HttpStatus.CREATED).bodyValueAndAwait(response)
            }
        }

    suspend fun list(request: ServerRequest): ServerResponse =
        handleErrors(withWebClient = true) {
            ServerResponse.ok().bodyValueAndAwait(workoutPlanService.list(currentUserId(request)))
        }

    suspend fun import(request: ServerRequest): ServerResponse =
        handleErrors(withWebClient = true) {
            val multipartData = request.multipartData().awaitSingle()
            val filePart =
                multipartData.getFirst("file") as? FilePart
                    ?: return@handleErrors ServerResponse
                        .badRequest()
                        .bodyValueAndAwait(ErrorResponse("Missing 'file' part"))

            val response = planImportService.import(currentUserId(request), filePart)
            ServerResponse.status(HttpStatus.CREATED).bodyValueAndAwait(response)
        }

    suspend fun getDetail(request: ServerRequest): ServerResponse =
        handleErrors(withWebClient = true) {
            val response =
                workoutPlanService.getDetail(
                    currentUserId(request),
                    UUID.fromString(request.pathVariable("planId")),
                )
            ServerResponse.ok().bodyValueAndAwait(response)
        }

    suspend fun update(request: ServerRequest): ServerResponse =
        handleErrors(withWebClient = true) {
            val body = request.awaitBody<UpdatePlanRequest>()
            validateOrBadRequest(validator, body) {
                val response =
                    workoutPlanService.update(
                        currentUserId(request),
                        UUID.fromString(request.pathVariable("planId")),
                        body,
                    )
                ServerResponse.ok().bodyValueAndAwait(response)
            }
        }

    suspend fun delete(request: ServerRequest): ServerResponse =
        handleErrors(withWebClient = true) {
            workoutPlanService.delete(
                currentUserId(request),
                UUID.fromString(request.pathVariable("planId")),
            )
            ServerResponse.noContent().buildAndAwait()
        }

    suspend fun activate(request: ServerRequest): ServerResponse =
        handleErrors(withWebClient = true) {
            workoutPlanService.activate(
                currentUserId(request),
                UUID.fromString(request.pathVariable("planId")),
            )
            ServerResponse.noContent().buildAndAwait()
        }
}
