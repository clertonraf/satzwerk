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
private const val DEFAULT_TOP_EXERCISES_LIMIT = 5
private const val MIN_TOP_EXERCISES_LIMIT = 1
private const val MAX_TOP_EXERCISES_LIMIT = 50

private fun parseDate(
    param: String,
    value: String,
): LocalDate =
    try {
        LocalDate.parse(value)
    } catch (_: DateTimeParseException) {
        throw BadRequestException("Invalid date for '$param': '$value'. Expected format: yyyy-MM-dd")
    }

private fun parseIntParam(
    ctx: RequestContext,
    name: String,
    default: Int,
    min: Int,
    max: Int,
): Int {
    val value =
        ctx.queryParam(name)?.let {
            it.toIntOrNull() ?: throw BadRequestException("'$name' must be a valid integer")
        } ?: default
    if (value !in min..max) throw BadRequestException("'$name' must be between $min and $max")
    return value
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
                        val weeks = parseIntParam(ctx, "weeks", DEFAULT_TREND_WEEKS, MIN_TREND_WEEKS, MAX_TREND_WEEKS)
                        ServerResponse.ok().bodyValueAndAwait(analyticsService.weeklyTrend(ctx.userId(), weeks))
                    }
                }
                GET("/personal-records") { request ->
                    handleErrors {
                        val ctx = RequestContext(request)
                        val limit = parseIntParam(ctx, "limit", DEFAULT_PR_LIMIT, MIN_PR_LIMIT, MAX_PR_LIMIT)
                        ServerResponse.ok().bodyValueAndAwait(analyticsService.personalRecords(ctx.userId(), limit))
                    }
                }
            }
        }

    @Bean
    fun analyticsExercisesRoutes(analyticsService: AnalyticsService) =
        coRouter {
            "/api/analytics".nest {
                GET("/top-exercises") { request ->
                    handleErrors {
                        val ctx = RequestContext(request)
                        val limit =
                            parseIntParam(
                                ctx,
                                "limit",
                                DEFAULT_TOP_EXERCISES_LIMIT,
                                MIN_TOP_EXERCISES_LIMIT,
                                MAX_TOP_EXERCISES_LIMIT,
                            )
                        ServerResponse.ok().bodyValueAndAwait(analyticsService.topExercises(ctx.userId(), limit))
                    }
                }
                GET("/least-exercises") { request ->
                    handleErrors {
                        val ctx = RequestContext(request)
                        val limit =
                            parseIntParam(
                                ctx,
                                "limit",
                                DEFAULT_TOP_EXERCISES_LIMIT,
                                MIN_TOP_EXERCISES_LIMIT,
                                MAX_TOP_EXERCISES_LIMIT,
                            )
                        ServerResponse.ok().bodyValueAndAwait(analyticsService.leastExercises(ctx.userId(), limit))
                    }
                }
                GET("/exercises/{exerciseId}/progress") { request ->
                    handleErrors {
                        val ctx = RequestContext(request)
                        ServerResponse.ok().bodyValueAndAwait(
                            analyticsService.exerciseProgress(ctx.userId(), ctx.pathId("exerciseId")),
                        )
                    }
                }
            }
        }
}
