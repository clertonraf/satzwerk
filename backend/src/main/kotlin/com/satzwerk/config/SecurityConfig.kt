package com.satzwerk.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.SecurityWebFiltersOrder
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.web.server.SecurityWebFilterChain
import reactor.core.publisher.Mono

@Configuration
@EnableWebFluxSecurity
class SecurityConfig(
    private val jwtAuthenticationWebFilter: JwtAuthenticationWebFilter,
    private val partnerTokenWebFilter: PartnerTokenWebFilter,
) {
    @Bean
    fun securityWebFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain =
        http
            .csrf { it.disable() }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .exceptionHandling {
                it.authenticationEntryPoint { exchange, _ ->
                    exchange.response.statusCode = HttpStatus.UNAUTHORIZED
                    exchange.response.headers.remove(HttpHeaders.WWW_AUTHENTICATE)
                    exchange.response.headers.contentType = MediaType.APPLICATION_JSON
                    val body =
                        exchange.response.bufferFactory()
                            .wrap("""{"message":"Unauthorized","error":"Unauthorized"}""".toByteArray(Charsets.UTF_8))
                    exchange.response.writeWith(Mono.just(body))
                }
            }
            // JWT filter: resolves `Authorization: Bearer <jwt>` → first-party user principal.
            .addFilterAt(jwtAuthenticationWebFilter, SecurityWebFiltersOrder.AUTHENTICATION)
            // Partner filter: resolves `X-App-Token: <opaque>` → partner principal (app+user bound).
            // It runs before AUTHORIZATION and keys on a different header than the JWT filter,
            // so no ordering conflict arises in practice.
            .addFilterBefore(partnerTokenWebFilter, SecurityWebFiltersOrder.AUTHORIZATION)
            .authorizeExchange {
                it.pathMatchers("/api/auth/**", "/actuator/**").permitAll()
                it.anyExchange().authenticated()
            }
            .build()
}
