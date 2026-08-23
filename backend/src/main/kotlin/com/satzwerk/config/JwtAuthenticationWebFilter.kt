package com.satzwerk.config

import com.satzwerk.auth.JwtService
import com.satzwerk.auth.PersonalApiToken
import com.satzwerk.auth.PersonalApiTokenService
import kotlinx.coroutines.reactor.mono
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

private const val PAT_PREFIX = "satzwerk_"

/**
 * Granted authority present only on JWT-authenticated sessions.
 * Management routes (token CRUD) require this authority so that personal API tokens
 * cannot be used to create, list, or revoke other tokens.
 * #205 (partner apps) should reuse this same authority name.
 */
const val AUTHORITY_JWT_SESSION = "JWT_SESSION"

@Component
class JwtAuthenticationWebFilter(
    private val jwtService: JwtService,
    private val personalApiTokenService: PersonalApiTokenService,
) : WebFilter {
    override fun filter(
        exchange: ServerWebExchange,
        chain: WebFilterChain,
    ): Mono<Void> {
        val bearerToken = extractBearerToken(exchange)
        val chainMono = chain.filter(exchange)
        return when {
            bearerToken.isBlank() -> chainMono
            bearerToken.startsWith(PAT_PREFIX) ->
                mono { personalApiTokenService.resolve(bearerToken) }
                    .flatMap { pat -> chainMono.withPatAuth(pat) }
                    .switchIfEmpty(Mono.defer { unauthorized(exchange) })
            else -> chainMono.withJwtAuth(bearerToken)
        }
    }

    private fun Mono<Void>.withPatAuth(pat: PersonalApiToken?): Mono<Void> {
        pat ?: return this
        // PAT authorities are the raw scope strings only — no JWT_SESSION marker.
        val authorities = pat.scopes().map { SimpleGrantedAuthority(it) }
        val auth = UsernamePasswordAuthenticationToken(pat.userId.toString(), pat, authorities)
        return contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth))
    }

    private fun Mono<Void>.withJwtAuth(bearerToken: String): Mono<Void> {
        val auth = jwtAuthFromToken(bearerToken) ?: return this
        return contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth))
    }

    private fun jwtAuthFromToken(bearerToken: String): Authentication? =
        try {
            val userId = jwtService.validateAccessToken(bearerToken)
            // JWT sessions carry the JWT_SESSION marker so management routes can enforce
            // first-party-session-only access.
            val authorities = listOf(SimpleGrantedAuthority(AUTHORITY_JWT_SESSION))
            UsernamePasswordAuthenticationToken(userId.toString(), bearerToken, authorities)
        } catch (_: Exception) {
            null
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

private fun extractBearerToken(exchange: ServerWebExchange): String =
    exchange.request.headers.getFirst(HttpHeaders.AUTHORIZATION)
        ?.takeIf { it.startsWith("Bearer ") }
        ?.removePrefix("Bearer ")
        ?.trim()
        .orEmpty()
