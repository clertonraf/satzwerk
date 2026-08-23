package com.satzwerk.analytics

import com.satzwerk.common.BadRequestException
import com.satzwerk.publicapi.PublicScope
import com.satzwerk.publicapi.handlePublicScope
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import org.springframework.web.reactive.function.server.coRouter
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

private const val DEFAULT_HEATMAP_MONTHS = 3L

private fun parseDate(
    param: String,
    value: String,
): LocalDate =
    try {
        LocalDate.parse(value)
    } catch (_: DateTimeParseException) {
        throw BadRequestException("Invalid date for '$param': '$value'. Expected format: yyyy-MM-dd")
    }

@Configuration
class PublicAnalyticsRouter {
    @Bean
    fun publicAnalyticsRoutes(publicAnalyticsService: PublicAnalyticsService) =
        coRouter {
            "/api/public/analytics".nest {
                /**
                 * Public heatmap endpoint — tracer bullet for personal automation token access.
                 * Requires a valid personal API token with the [PublicScope.ANALYTICS_READ] scope.
                 * Returns the same payload as the internal /api/analytics/heatmap.
                 */
                GET("/heatmap") { request ->
                    handlePublicScope(request, PublicScope.ANALYTICS_READ) { ctx ->
                        val today = LocalDate.now(ZoneOffset.UTC)
                        val from =
                            ctx.queryParam("from")?.let { parseDate("from", it) }
                                ?: today.minusMonths(DEFAULT_HEATMAP_MONTHS)
                        val to = ctx.queryParam("to")?.let { parseDate("to", it) } ?: today
                        ServerResponse.ok().bodyValueAndAwait(publicAnalyticsService.heatmap(ctx.userId(), from, to))
                    }
                }
            }
        }
}
