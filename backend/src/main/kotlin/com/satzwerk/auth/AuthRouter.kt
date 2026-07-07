package com.satzwerk.auth

import com.satzwerk.common.ErrorResponse
import com.satzwerk.common.RequestContext
import com.satzwerk.common.body
import com.satzwerk.common.handleErrors
import com.satzwerk.common.validateOrBadRequest
import jakarta.validation.Validator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import org.springframework.web.reactive.function.server.coRouter

@Configuration
class AuthRouter {
    @Bean
    fun authRoutes(
        authService: AuthService,
        validator: Validator,
    ) = coRouter {
        "/api/auth".nest {
            POST("/register") { request ->
                handleErrors {
                    val ctx = RequestContext(request)
                    try {
                        val body = ctx.body<RegisterRequest>()
                        validateOrBadRequest(validator, body) {
                            val tokenPair = authService.register(body.email, body.password, body.displayName)
                            ServerResponse.status(HttpStatus.CREATED)
                                .bodyValueAndAwait(AuthResponse(tokenPair.accessToken, tokenPair.refreshToken))
                        }
                    } catch (_: DuplicateEmailException) {
                        ServerResponse.status(HttpStatus.CONFLICT)
                            .bodyValueAndAwait(ErrorResponse("Email already registered"))
                    }
                }
            }
            POST("/login") { request ->
                handleErrors {
                    val ctx = RequestContext(request)
                    try {
                        val body = ctx.body<LoginRequest>()
                        validateOrBadRequest(validator, body) {
                            val tokenPair = authService.login(body.email, body.password)
                            ServerResponse.ok().bodyValueAndAwait(
                                AuthResponse(tokenPair.accessToken, tokenPair.refreshToken),
                            )
                        }
                    } catch (_: InvalidCredentialsException) {
                        ServerResponse.status(HttpStatus.UNAUTHORIZED)
                            .bodyValueAndAwait(ErrorResponse("Invalid credentials"))
                    }
                }
            }
            POST("/refresh") { request ->
                handleErrors {
                    val ctx = RequestContext(request)
                    try {
                        val body = ctx.body<RefreshRequest>()
                        validateOrBadRequest(validator, body) {
                            val tokenPair = authService.refresh(body.refreshToken)
                            ServerResponse.ok().bodyValueAndAwait(
                                AuthResponse(tokenPair.accessToken, tokenPair.refreshToken),
                            )
                        }
                    } catch (_: InvalidRefreshTokenException) {
                        ServerResponse.status(HttpStatus.UNAUTHORIZED)
                            .bodyValueAndAwait(ErrorResponse("Invalid refresh token"))
                    }
                }
            }
        }
    }
}
