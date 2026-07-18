package com.satzwerk.measurements

import com.satzwerk.common.BadRequestException
import com.satzwerk.common.RequestContext
import com.satzwerk.common.body
import com.satzwerk.common.handleErrors
import com.satzwerk.common.validateOrBadRequest
import jakarta.validation.Validator
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import org.springframework.web.reactive.function.server.buildAndAwait
import java.time.LocalDate
import java.time.format.DateTimeParseException

class MeasurementHandler(
    private val measurementService: MeasurementService,
    private val validator: Validator,
) {
    suspend fun upsert(request: ServerRequest): ServerResponse =
        handleErrors {
            val ctx = RequestContext(request)
            val body = ctx.body<UpsertMeasurementRequest>()
            validateOrBadRequest(validator, body) {
                val response = measurementService.upsert(ctx.userId(), body)
                ServerResponse.ok().bodyValueAndAwait(response)
            }
        }

    suspend fun findAll(request: ServerRequest): ServerResponse =
        handleErrors {
            val ctx = RequestContext(request)
            ServerResponse.ok().bodyValueAndAwait(measurementService.findAll(ctx.userId()))
        }

    suspend fun deleteByDate(request: ServerRequest): ServerResponse =
        handleErrors {
            val ctx = RequestContext(request)
            val date = parseDate(request.pathVariable("date"))
            measurementService.deleteByDate(ctx.userId(), date)
            ServerResponse.noContent().buildAndAwait()
        }
}

private fun parseDate(value: String): LocalDate =
    try {
        LocalDate.parse(value)
    } catch (_: DateTimeParseException) {
        throw BadRequestException("Invalid date format: expected yyyy-MM-dd, got '$value'")
    }
