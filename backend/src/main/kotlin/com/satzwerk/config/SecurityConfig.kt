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
            .addFilterAt(jwtAuthenticationWebFilter, SecurityWebFiltersOrder.AUTHENTICATION)
            .authorizeExchange {
                it.pathMatchers("/api/auth/**", "/actuator/**").permitAll()
                it.anyExchange().authenticated()
            }
            .build()
}
