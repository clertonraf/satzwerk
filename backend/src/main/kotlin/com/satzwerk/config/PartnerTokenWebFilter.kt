package com.satzwerk.config

import com.satzwerk.partners.PartnerAppService
import com.satzwerk.partners.PartnerPrincipal
import kotlinx.coroutines.reactor.mono
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

internal const val APP_TOKEN_HEADER = "X-App-Token"

/** The only path a partner token is permitted to authenticate. */
private const val PARTNER_PROBE_PATH = "/api/partner-grants/me"

/**
 * Authenticates partner-app requests that present an [APP_TOKEN_HEADER] credential,
 * **only for the tightly scoped probe path** [PARTNER_PROBE_PATH].
 *
 * All grant-management routes remain JWT-session-only — this filter skips them,
 * so presenting a partner token to a management route yields 401 from the security chain.
 *
 * On success, places a [UsernamePasswordAuthenticationToken] whose:
 * - **principal** = [PartnerPrincipal] whose `name` is the consenting-user UUID string
 *   → [com.satzwerk.common.RequestContext.userId] works unchanged
 * - **credentials** = opaque app token string (not used downstream)
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
        val rawToken =
            exchange.request.uri.path
                .takeIf { it == PARTNER_PROBE_PATH }
                ?.let { exchange.request.headers.getFirst(APP_TOKEN_HEADER)?.trim() }
                .orEmpty()

        if (rawToken.isBlank()) {
            return chain.filter(exchange)
        }

        return mono { partnerAppService.resolveActiveGrant(rawToken) }
            .flatMap { grant ->
                if (grant == null) {
                    chain.filter(exchange)
                } else {
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
                            // principal.name == userId UUID string; RequestContext.userId() parses it ✓
                            partnerPrincipal,
                            rawToken,
                            scopeAuthorities,
                        )
                    chain.filter(exchange)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication))
                }
            }
    }
}
