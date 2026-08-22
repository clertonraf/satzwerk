package com.satzwerk.config

import com.satzwerk.partners.PartnerAppService
import com.satzwerk.partners.PartnerPrincipal
import kotlinx.coroutines.reactor.mono
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

internal const val APP_TOKEN_HEADER = "X-App-Token"

/** The exact probe path where a partner token may be presented. */
private const val PARTNER_PROBE_PATH = "/api/partner-grants/me"

/** Path prefix for public API routes that also accept partner tokens. */
private const val PUBLIC_API_PREFIX = "/api/public/"

/**
 * Authenticates partner-app requests that present an [APP_TOKEN_HEADER] credential on the
 * exact probe path [PARTNER_PROBE_PATH] and on public API routes under [PUBLIC_API_PREFIX].
 *
 * All grant-management routes remain JWT-session-only — this filter skips them,
 * so presenting a partner token to a management route yields 401 from the security chain.
 *
 * On success, places a [UsernamePasswordAuthenticationToken] whose:
 * - **principal** = [PartnerPrincipal] whose `name` is the consenting-user UUID string;
 *   cast to [PartnerPrincipal] for app/grant context via [com.satzwerk.common.requirePartnerPrincipal]
 * - **authorities** = raw scope strings (e.g. `"exercises:read"`) — no `SCOPE_` prefix,
 *   aligned with the shared convention in ADR-0005 / #204.
 *
 * Revoked or unknown tokens return the same JSON 401 shape as the security entry point.
 */
@Component
class PartnerTokenWebFilter(
    private val partnerAppService: PartnerAppService,
) : WebFilter {
    override fun filter(
        exchange: ServerWebExchange,
        chain: WebFilterChain,
    ): Mono<Void> {
        val path = exchange.request.uri.path
        val rawToken =
            exchange.request.headers
                .getFirst(APP_TOKEN_HEADER)
                ?.trim()
                ?.takeIf { path == PARTNER_PROBE_PATH || path.startsWith(PUBLIC_API_PREFIX) }
                .orEmpty()

        if (rawToken.isBlank()) {
            return chain.filter(exchange)
        }

        return mono { partnerAppService.resolveActiveGrant(rawToken) }
            .flatMap { grant ->
                val partnerPrincipal =
                    PartnerPrincipal(
                        userId = grant.userId.toString(),
                        appId = grant.appId.toString(),
                        grantId = requireNotNull(grant.id).toString(),
                        grantedScopes = grant.grantedScopes,
                    )
                val scopeAuthorities =
                    grant.grantedScopes
                        .split(" ")
                        .filter { it.isNotBlank() }
                        .map { SimpleGrantedAuthority(it) }
                val authentication =
                    UsernamePasswordAuthenticationToken(
                        partnerPrincipal,
                        "",
                        scopeAuthorities,
                    )
                chain.filter(exchange)
                    .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication))
            }
            .switchIfEmpty(Mono.defer { unauthorized(exchange) })
    }

    private fun unauthorized(exchange: ServerWebExchange): Mono<Void> {
        exchange.response.statusCode = HttpStatus.UNAUTHORIZED
        exchange.response.headers.remove(HttpHeaders.WWW_AUTHENTICATE)
        exchange.response.headers.contentType = MediaType.APPLICATION_JSON
        val body =
            exchange.response.bufferFactory()
                .wrap("""{"message":"Unauthorized","error":"Unauthorized"}""".toByteArray(Charsets.UTF_8))
        return exchange.response.writeWith(Mono.just(body))
    }
}
