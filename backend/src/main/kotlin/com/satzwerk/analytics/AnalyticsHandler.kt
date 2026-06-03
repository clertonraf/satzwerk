package com.satzwerk.analytics

import com.satzwerk.common.BadRequestException
import com.satzwerk.common.currentUserId
import com.satzwerk.common.handleErrors
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

private const val DEFAULT_HEATMAP_MONTHS = 3L

@Component
class AnalyticsHandler(
    private val analyticsService: AnalyticsService,
) {
    suspend fun heatmap(request: ServerRequest): ServerResponse =
        handleErrors {
            val today = LocalDate.now(ZoneOffset.UTC)
            val from =
                request.queryParam("from").map { parseDate("from", it) }
                    .orElse(today.minusMonths(DEFAULT_HEATMAP_MONTHS))
            val to = request.queryParam("to").map { parseDate("to", it) }.orElse(today)
            val userId = currentUserId(request)
            ServerResponse.ok().bodyValueAndAwait(analyticsService.heatmap(userId, from, to))
        }

    suspend fun streak(request: ServerRequest): ServerResponse {
        val userId = currentUserId(request)
        return ServerResponse.ok().bodyValueAndAwait(analyticsService.streak(userId))
    }

    private fun parseDate(
        param: String,
        value: String,
    ): LocalDate =
        try {
            LocalDate.parse(value)
        } catch (_: DateTimeParseException) {
            throw BadRequestException("Invalid date for '$param': '$value'. Expected format: yyyy-MM-dd")
        }
}
