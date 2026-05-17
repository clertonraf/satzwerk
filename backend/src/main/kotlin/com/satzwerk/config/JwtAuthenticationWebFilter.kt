package com.satzwerk.config

import com.satzwerk.auth.JwtService
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

@Component
class JwtAuthenticationWebFilter(
    private val jwtService: JwtService,
) : WebFilter {
    override fun filter(
        exchange: ServerWebExchange,
        chain: WebFilterChain,
    ): Mono<Void> {
        val bearerToken =
            exchange.request.headers.getFirst(HttpHeaders.AUTHORIZATION)
                ?.takeIf { it.startsWith("Bearer ") }
                ?.removePrefix("Bearer ")
                ?.trim()
                .orEmpty()

        if (bearerToken.isBlank()) {
            return chain.filter(exchange)
        }

        return try {
            val userId = jwtService.validateAccessToken(bearerToken)
            val authentication = UsernamePasswordAuthenticationToken(userId.toString(), bearerToken, emptyList())
            chain.filter(exchange).contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication))
        } catch (_: Exception) {
            chain.filter(exchange)
        }
    }
}
