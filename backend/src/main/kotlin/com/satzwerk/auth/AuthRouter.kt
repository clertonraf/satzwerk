package com.satzwerk.auth

import com.satzwerk.common.ErrorResponse
import com.satzwerk.common.RequestContext
import com.satzwerk.common.body
import com.satzwerk.common.handleErrors
import com.satzwerk.common.validateOrBadRequest
import jakarta.validation.Validator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.awaitBodyOrNull
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import org.springframework.web.reactive.function.server.buildAndAwait
import org.springframework.web.reactive.function.server.coRouter

@Configuration
class AuthRouter {
    @Bean
    fun authRoutes(
        authService: AuthService,
        validator: Validator,
        environment: Environment,
    ) = coRouter {
        "/api/auth".nest {
            POST("/register") { request -> register(request, authService, validator, environment) }
            POST("/login") { request -> login(request, authService, validator, environment) }
            POST("/refresh") { request -> refresh(request, authService, environment) }
            POST("/logout") { request -> logout(request, authService, environment) }
        }
    }
}

private suspend fun register(
    request: ServerRequest,
    authService: AuthService,
    validator: Validator,
    environment: Environment,
): ServerResponse =
    handleErrors {
        val ctx = RequestContext(request)
        try {
            val body = ctx.body<RegisterRequest>()
            validateOrBadRequest(validator, body) {
                authResponse(
                    status = HttpStatus.CREATED,
                    tokenPair = authService.register(body.email, body.password, body.displayName),
                    authService = authService,
                    environment = environment,
                )
            }
        } catch (_: DuplicateEmailException) {
            ServerResponse.status(HttpStatus.CONFLICT)
                .bodyValueAndAwait(ErrorResponse("Email already registered"))
        }
    }

private suspend fun login(
    request: ServerRequest,
    authService: AuthService,
    validator: Validator,
    environment: Environment,
): ServerResponse =
    handleErrors {
        val ctx = RequestContext(request)
        try {
            val body = ctx.body<LoginRequest>()
            validateOrBadRequest(validator, body) {
                authResponse(
                    status = HttpStatus.OK,
                    tokenPair = authService.login(body.email, body.password),
                    authService = authService,
                    environment = environment,
                )
            }
        } catch (_: InvalidCredentialsException) {
            ServerResponse.status(HttpStatus.UNAUTHORIZED)
                .bodyValueAndAwait(ErrorResponse("Invalid credentials"))
        }
    }

private suspend fun refresh(
    request: ServerRequest,
    authService: AuthService,
    environment: Environment,
): ServerResponse =
    handleErrors {
        try {
            val legacyRequest = request.awaitBodyOrNull<LegacyRefreshRequest>()
            val refreshToken = request.cookieValue(REFRESH_COOKIE_NAME)

            if (refreshToken == null && !legacyRequest?.refreshToken.isNullOrBlank()) {
                return@handleErrors ServerResponse.status(HttpStatus.FORBIDDEN)
                    .bodyValueAndAwait(ErrorResponse("Missing CSRF token header"))
            }

            authResponse(
                status = HttpStatus.OK,
                tokenPair =
                    authService.refresh(
                        rawRefreshToken = refreshToken ?: throw InvalidRefreshTokenException(),
                        csrfHeaderToken = request.headers().firstHeader(REFRESH_CSRF_HEADER_NAME),
                        csrfCookieToken = request.cookieValue(REFRESH_CSRF_COOKIE_NAME),
                    ),
                authService = authService,
                environment = environment,
            )
        } catch (_: InvalidRefreshTokenException) {
            ServerResponse.status(HttpStatus.UNAUTHORIZED)
                .bodyValueAndAwait(ErrorResponse("Invalid refresh token"))
        }
    }

private suspend fun logout(
    request: ServerRequest,
    authService: AuthService,
    environment: Environment,
): ServerResponse =
    handleErrors {
        authService.logout(request.cookieValue(REFRESH_COOKIE_NAME))
        ServerResponse.noContent()
            .cookie(clearRefreshTokenCookie(environment))
            .cookie(clearRefreshCsrfCookie(environment))
            .buildAndAwait()
    }

private suspend fun authResponse(
    status: HttpStatus,
    tokenPair: TokenPair,
    authService: AuthService,
    environment: Environment,
): ServerResponse {
    val maxAgeSeconds = authService.refreshTokenMaxAgeSeconds()

    return ServerResponse.status(status)
        .cookie(refreshTokenCookie(tokenPair.refreshToken, maxAgeSeconds, environment))
        .cookie(refreshCsrfCookie(tokenPair.csrfToken, maxAgeSeconds, environment))
        .bodyValueAndAwait(AuthResponse(tokenPair.accessToken, tokenPair.csrfToken))
}

private fun ServerRequest.cookieValue(name: String): String? = cookies().getFirst(name)?.value

private data class LegacyRefreshRequest(
    val refreshToken: String? = null,
)
