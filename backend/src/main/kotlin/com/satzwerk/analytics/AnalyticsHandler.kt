package com.satzwerk.analytics

import com.satzwerk.common.currentUserId
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import java.time.LocalDate
import java.time.ZoneOffset

private const val DEFAULT_HEATMAP_WEEKS = 52L

@Component
class AnalyticsHandler(
    private val analyticsService: AnalyticsService,
) {
    suspend fun heatmap(request: ServerRequest): ServerResponse {
        val today = LocalDate.now(ZoneOffset.UTC)
        val from = request.queryParam("from").map(LocalDate::parse).orElse(today.minusWeeks(DEFAULT_HEATMAP_WEEKS))
        val to = request.queryParam("to").map(LocalDate::parse).orElse(today)
        val userId = currentUserId(request)
        return ServerResponse.ok().bodyValueAndAwait(analyticsService.heatmap(userId, from, to))
    }

    suspend fun streak(request: ServerRequest): ServerResponse {
        val userId = currentUserId(request)
        return ServerResponse.ok().bodyValueAndAwait(analyticsService.streak(userId))
    }
}
