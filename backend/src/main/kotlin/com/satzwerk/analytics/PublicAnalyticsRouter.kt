package com.satzwerk.analytics

import com.satzwerk.auth.InsufficientScopeException
import com.satzwerk.auth.TokenScope
import com.satzwerk.common.BadRequestException
import com.satzwerk.common.RequestContext
import com.satzwerk.common.handleErrors
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.web.reactive.function.server.ServerRequest
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

/**
 * Checks that the resolved principal holds the required scope authority.
 * Throws [InsufficientScopeException] when the scope is absent.
 *
 * This is the minimal public-principal scope guard introduced for #204.
 * #205 (partner apps) should extend or replace this with a shared abstraction.
 */
private suspend fun requireScope(
    request: ServerRequest,
    scope: String,
) {
    val authentication = request.principal().awaitSingle()
    val authorities =
        (authentication as? UsernamePasswordAuthenticationToken)
            ?.authorities ?: emptyList()
    if (SimpleGrantedAuthority(scope) !in authorities) {
        throw InsufficientScopeException(scope)
    }
}

@Configuration
class PublicAnalyticsRouter {
    @Bean
    fun publicAnalyticsRoutes(analyticsService: AnalyticsService) =
        coRouter {
            "/api/public/analytics".nest {
                /**
                 * Public heatmap endpoint — tracer bullet for personal automation token access.
                 * Requires a valid personal API token with the [TokenScope.ANALYTICS_READ] scope.
                 * Returns the same payload as the internal /api/analytics/heatmap.
                 */
                GET("/heatmap") { request ->
                    handleErrors(
                        extra = mapOf(InsufficientScopeException::class to HttpStatus.FORBIDDEN),
                    ) {
                        requireScope(request, TokenScope.ANALYTICS_READ)
                        val ctx = RequestContext(request)
                        val today = LocalDate.now(ZoneOffset.UTC)
                        val from =
                            ctx.queryParam("from")?.let { parseDate("from", it) }
                                ?: today.minusMonths(DEFAULT_HEATMAP_MONTHS)
                        val to = ctx.queryParam("to")?.let { parseDate("to", it) } ?: today
                        ServerResponse.ok().bodyValueAndAwait(analyticsService.heatmap(ctx.userId(), from, to))
                    }
                }
            }
        }
}
