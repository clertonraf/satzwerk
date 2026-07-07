package com.satzwerk.analytics

import com.satzwerk.common.BadRequestException
import com.satzwerk.common.RequestContext
import com.satzwerk.common.handleErrors
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import org.springframework.web.reactive.function.server.coRouter
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

private const val DEFAULT_HEATMAP_MONTHS = 3L
private const val DEFAULT_TREND_WEEKS = 8
private const val MIN_TREND_WEEKS = 1
private const val MAX_TREND_WEEKS = 52
private const val DEFAULT_PR_LIMIT = 5
private const val MIN_PR_LIMIT = 1
private const val MAX_PR_LIMIT = 20

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
class AnalyticsRouter {
    @Bean
    fun analyticsRoutes(analyticsService: AnalyticsService) =
        coRouter {
            "/api/analytics".nest {
                GET("/heatmap") { request ->
                    handleErrors {
                        val ctx = RequestContext(request)
                        val today = LocalDate.now(ZoneOffset.UTC)
                        val from =
                            ctx.queryParam("from")?.let { parseDate("from", it) }
                                ?: today.minusMonths(DEFAULT_HEATMAP_MONTHS)
                        val to = ctx.queryParam("to")?.let { parseDate("to", it) } ?: today
                        ServerResponse.ok().bodyValueAndAwait(analyticsService.heatmap(ctx.userId(), from, to))
                    }
                }
                GET("/streak") { request ->
                    handleErrors {
                        val ctx = RequestContext(request)
                        ServerResponse.ok().bodyValueAndAwait(analyticsService.streak(ctx.userId()))
                    }
                }
                GET("/summary") { request ->
                    handleErrors {
                        val ctx = RequestContext(request)
                        ServerResponse.ok().bodyValueAndAwait(analyticsService.dashboardSummary(ctx.userId()))
                    }
                }
                GET("/weekly-trend") { request ->
                    handleErrors {
                        val ctx = RequestContext(request)
                        val weeks =
                            ctx.queryParam("weeks")?.let {
                                it.toIntOrNull() ?: throw BadRequestException("'weeks' must be a valid integer")
                            } ?: DEFAULT_TREND_WEEKS
                        if (weeks !in MIN_TREND_WEEKS..MAX_TREND_WEEKS) {
                            throw BadRequestException("'weeks' must be between $MIN_TREND_WEEKS and $MAX_TREND_WEEKS")
                        }
                        ServerResponse.ok().bodyValueAndAwait(analyticsService.weeklyTrend(ctx.userId(), weeks))
                    }
                }
                GET("/personal-records") { request ->
                    handleErrors {
                        val ctx = RequestContext(request)
                        val limit =
                            ctx.queryParam("limit")?.let {
                                it.toIntOrNull() ?: throw BadRequestException("'limit' must be a valid integer")
                            } ?: DEFAULT_PR_LIMIT
                        if (limit !in MIN_PR_LIMIT..MAX_PR_LIMIT) {
                            throw BadRequestException("'limit' must be between $MIN_PR_LIMIT and $MAX_PR_LIMIT")
                        }
                        ServerResponse.ok().bodyValueAndAwait(analyticsService.personalRecords(ctx.userId(), limit))
                    }
                }
            }
        }
}
