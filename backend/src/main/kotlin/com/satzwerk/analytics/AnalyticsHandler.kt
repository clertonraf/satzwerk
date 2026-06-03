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
private const val DEFAULT_TREND_WEEKS = 8
private const val DEFAULT_PR_LIMIT = 5

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

    suspend fun dashboardSummary(request: ServerRequest): ServerResponse {
        val userId = currentUserId(request)
        return ServerResponse.ok().bodyValueAndAwait(analyticsService.dashboardSummary(userId))
    }

    suspend fun weeklyTrend(request: ServerRequest): ServerResponse =
        handleErrors {
            val userId = currentUserId(request)
            val weeks = request.queryParam("weeks")
                .map { it.toIntOrNull() ?: DEFAULT_TREND_WEEKS }
                .orElse(DEFAULT_TREND_WEEKS)
            ServerResponse.ok().bodyValueAndAwait(analyticsService.weeklyTrend(userId, weeks))
        }

    suspend fun personalRecords(request: ServerRequest): ServerResponse =
        handleErrors {
            val userId = currentUserId(request)
            val limit = request.queryParam("limit")
                .map { it.toIntOrNull() ?: DEFAULT_PR_LIMIT }
                .orElse(DEFAULT_PR_LIMIT)
            ServerResponse.ok().bodyValueAndAwait(analyticsService.personalRecords(userId, limit))
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
