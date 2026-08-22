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

/** Paths where a partner app token is a valid credential (in addition to JWT). */
private val PARTNER_ALLOWED_PATH_PREFIXES =
    listOf(
        "/api/partner-grants/me",
        "/api/public/",
    )

/**
 * Authenticates partner-app requests that present an [APP_TOKEN_HEADER] credential on the
 * exact probe path `/api/partner-grants/me` and on public API routes under `/api/public/`.
 *
 * All grant-management routes remain JWT-session-only — this filter skips them,
 * so presenting a partner token to a management route yields 401 from the security chain.
 *
 * On success, places a [UsernamePasswordAuthenticationToken] whose:
 * - **principal** = consenting-user UUID string → [com.satzwerk.common.RequestContext.userId] ✓
 * - **credentials** = [PartnerPrincipal] → app/scope context for callers that need it
 * - **authorities** = raw scope strings (e.g. `"exercises:read"`) — no `SCOPE_` prefix,
 *   aligned with the shared convention in ADR-0005 / #204.
 *
 * Revoked or unknown tokens produce no authentication; the security chain returns 401.
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
            path
                .takeIf { p ->
                    PARTNER_ALLOWED_PATH_PREFIXES.any { prefix ->
                        if (prefix.endsWith("/")) p.startsWith(prefix) else p == prefix
                    }
                }
                ?.let { exchange.request.headers.getFirst(APP_TOKEN_HEADER)?.trim() }
                .orEmpty()

        if (rawToken.isBlank()) {
            return chain.filter(exchange)
        }

        return mono { partnerAppService.resolveActiveGrant(rawToken) }
            .switchIfEmpty(Mono.defer { unauthorized(exchange).then(Mono.empty()) })
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
                        grant.userId.toString(),
                        partnerPrincipal,
                        scopeAuthorities,
                    )
                chain.filter(exchange)
                    .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication))
            }
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
