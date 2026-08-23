package com.satzwerk.medications

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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

private const val DEFAULT_LOG_WINDOW_SECONDS = 30L * 24 * 3600
private const val DEFAULT_HEATMAP_WEEKS = 52
private const val DEFAULT_JOURNAL_DAYS = 30L
private const val SECONDS_PER_MINUTE = 60

class MedicationHandler(
    private val medicationService: MedicationService,
    private val medicationAnalyticsService: MedicationAnalyticsService,
    private val validator: Validator,
) {
    suspend fun create(request: ServerRequest): ServerResponse =
        handleErrors {
            val ctx = RequestContext(request)
            val body = ctx.body<CreateMedicationRequest>()
            validateOrBadRequest(validator, body) {
                ServerResponse.ok().bodyValueAndAwait(medicationService.createMedication(ctx.userId(), body))
            }
        }

    suspend fun getAll(request: ServerRequest): ServerResponse =
        handleErrors {
            val ctx = RequestContext(request)
            ServerResponse.ok().bodyValueAndAwait(medicationService.getMedications(ctx.userId()))
        }

    suspend fun getOne(request: ServerRequest): ServerResponse =
        handleErrors {
            val ctx = RequestContext(request)
            val id = ctx.pathId("id")
            ServerResponse.ok().bodyValueAndAwait(medicationService.getMedication(ctx.userId(), id))
        }

    suspend fun update(request: ServerRequest): ServerResponse =
        handleErrors {
            val ctx = RequestContext(request)
            val id = ctx.pathId("id")
            val body = ctx.body<UpdateMedicationRequest>()
            validateOrBadRequest(validator, body) {
                ServerResponse.ok().bodyValueAndAwait(medicationService.updateMedication(ctx.userId(), id, body))
            }
        }

    suspend fun deactivate(request: ServerRequest): ServerResponse =
        handleErrors {
            val ctx = RequestContext(request)
            val id = ctx.pathId("id")
            medicationService.deactivateMedication(ctx.userId(), id)
            ServerResponse.noContent().buildAndAwait()
        }

    suspend fun getToday(request: ServerRequest): ServerResponse =
        handleErrors {
            val ctx = RequestContext(request)
            ServerResponse.ok().bodyValueAndAwait(medicationService.getTodayView(ctx.userId()))
        }

    suspend fun logDose(request: ServerRequest): ServerResponse =
        handleErrors {
            val ctx = RequestContext(request)
            val medicationId = ctx.pathId("id")
            val body = ctx.body<LogDoseRequest>()
            validateOrBadRequest(validator, body) {
                ServerResponse.ok().bodyValueAndAwait(medicationService.logDose(ctx.userId(), medicationId, body))
            }
        }

    suspend fun getLogs(request: ServerRequest): ServerResponse =
        handleErrors {
            val ctx = RequestContext(request)
            val medicationId = ctx.pathId("id")
            val from = parseInstantParam(request, "from") ?: Instant.now().minusSeconds(DEFAULT_LOG_WINDOW_SECONDS)
            val to = parseInstantParam(request, "to") ?: Instant.now()
            ServerResponse.ok().bodyValueAndAwait(
                medicationService.getLogsForMedication(ctx.userId(), medicationId, from, to),
            )
        }

    suspend fun getAggregateHeatmap(request: ServerRequest): ServerResponse =
        handleErrors {
            val ctx = RequestContext(request)
            val weeks =
                request
                    .queryParam("weeks")
                    .map { it.toIntOrNull() ?: DEFAULT_HEATMAP_WEEKS }
                    .orElse(DEFAULT_HEATMAP_WEEKS)
            if (weeks <= 0) throw BadRequestException("weeks must be a positive integer")
            ServerResponse.ok().bodyValueAndAwait(
                medicationAnalyticsService.getAggregateHeatmap(ctx.userId(), weeks),
            )
        }

    suspend fun getPerMedicationAnalytics(request: ServerRequest): ServerResponse =
        handleErrors {
            val ctx = RequestContext(request)
            val medicationId = ctx.pathId("id")
            val granularity =
                request.queryParam("granularity")
                    .map { parseGranularity(it) }
                    .orElse(BarChartGranularity.WEEKLY)
            ServerResponse.ok().bodyValueAndAwait(
                medicationAnalyticsService.getPerMedicationAnalytics(medicationId, ctx.userId(), granularity),
            )
        }
}

private fun parseInstantParam(
    request: ServerRequest,
    name: String,
): Instant? =
    request.queryParam(name).map { value ->
        try {
            Instant.parse(value)
        } catch (_: DateTimeParseException) {
            throw BadRequestException("Invalid $name format: expected ISO-8601, got '$value'")
        }
    }.orElse(null)

private fun parseGranularity(value: String): BarChartGranularity =
    try {
        BarChartGranularity.valueOf(value.uppercase())
    } catch (_: IllegalArgumentException) {
        throw BadRequestException("Invalid granularity: '$value'. Expected WEEKLY or MONTHLY.")
    }

/** Package-level handler to keep [MedicationHandler] under the detekt TooManyFunctions limit. */
internal suspend fun journalHandler(
    request: ServerRequest,
    medicationService: MedicationService,
): ServerResponse =
    handleErrors {
        val ctx = RequestContext(request)
        val zoneOffset = parseZoneOffsetMinutes(request)
        val today = LocalDate.now(zoneOffset)
        val from =
            request.queryParam("from").map { value ->
                try {
                    parseLocalDateStart(value, zoneOffset)
                } catch (_: DateTimeParseException) {
                    throw BadRequestException("Invalid from date: '$value'. Expected yyyy-MM-dd.")
                }
            }.orElse(parseLocalDateStart(today.minusDays(DEFAULT_JOURNAL_DAYS).toString(), zoneOffset))
        val to =
            request.queryParam("to").map { value ->
                try {
                    parseLocalDateEnd(value, zoneOffset)
                } catch (_: DateTimeParseException) {
                    throw BadRequestException("Invalid to date: '$value'. Expected yyyy-MM-dd.")
                }
            }.orElse(parseLocalDateEnd(today.toString(), zoneOffset))
        ServerResponse.ok().bodyValueAndAwait(medicationService.getJournalView(ctx.userId(), from, to, zoneOffset))
    }

private fun parseZoneOffsetMinutes(request: ServerRequest): ZoneOffset =
    request.queryParam("timezoneOffsetMinutes").map { value ->
        val offsetMinutes =
            value.toIntOrNull()
                ?: throw BadRequestException(
                    "Invalid timezoneOffsetMinutes: '$value'. Expected an integer number of minutes.",
                )
        try {
            // JS getTimezoneOffset() is UTC-local, so negate it to obtain the local ZoneOffset.
            ZoneOffset.ofTotalSeconds(-offsetMinutes * SECONDS_PER_MINUTE)
        } catch (_: IllegalArgumentException) {
            throw BadRequestException("Invalid timezoneOffsetMinutes: '$value'. Expected a valid UTC offset.")
        }
    }.orElse(ZoneOffset.UTC)

private fun parseLocalDateStart(
    value: String,
    zoneOffset: ZoneOffset,
): Instant = LocalDate.parse(value).atStartOfDay().atOffset(zoneOffset).toInstant()

private fun parseLocalDateEnd(
    value: String,
    zoneOffset: ZoneOffset,
): Instant = LocalDate.parse(value).plusDays(1).atStartOfDay().atOffset(zoneOffset).toInstant().minusNanos(1)
