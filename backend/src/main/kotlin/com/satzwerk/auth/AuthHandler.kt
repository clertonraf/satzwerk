package com.satzwerk.auth

import jakarta.validation.Validator
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.awaitBody
import org.springframework.web.reactive.function.server.bodyValueAndAwait

private data class ErrorResponse(
    val error: String,
)

private data class ValidationErrorResponse(
    val errors: Map<String, String>,
)

@Component
class AuthHandler(
    private val authService: AuthService,
    private val validator: Validator,
) {
    suspend fun register(request: ServerRequest): ServerResponse {
        return try {
            val body = request.awaitBody<RegisterRequest>()
            val violations = validator.validate(body)
            if (violations.isNotEmpty()) {
                ServerResponse
                    .badRequest()
                    .bodyValueAndAwait(
                        ValidationErrorResponse(
                            violations.associate { it.propertyPath.toString() to it.message },
                        ),
                    )
            } else {
                val tokenPair = authService.register(body.email, body.password, body.displayName)
                ServerResponse
                    .status(HttpStatus.CREATED)
                    .bodyValueAndAwait(AuthResponse(tokenPair.accessToken, tokenPair.refreshToken))
            }
        } catch (_: DuplicateEmailException) {
            ServerResponse
                .status(HttpStatus.CONFLICT)
                .bodyValueAndAwait(ErrorResponse("Email already registered"))
        }
    }

    suspend fun login(request: ServerRequest): ServerResponse {
        return try {
            val body = request.awaitBody<LoginRequest>()
            val violations = validator.validate(body)
            if (violations.isNotEmpty()) {
                ServerResponse
                    .badRequest()
                    .bodyValueAndAwait(
                        ValidationErrorResponse(
                            violations.associate { it.propertyPath.toString() to it.message },
                        ),
                    )
            } else {
                val tokenPair = authService.login(body.email, body.password)
                ServerResponse.ok().bodyValueAndAwait(AuthResponse(tokenPair.accessToken, tokenPair.refreshToken))
            }
        } catch (_: InvalidCredentialsException) {
            ServerResponse.status(HttpStatus.UNAUTHORIZED).bodyValueAndAwait(ErrorResponse("Invalid credentials"))
        }
    }

    suspend fun refresh(request: ServerRequest): ServerResponse {
        return try {
            val body = request.awaitBody<RefreshRequest>()
            val violations = validator.validate(body)
            if (violations.isNotEmpty()) {
                ServerResponse
                    .badRequest()
                    .bodyValueAndAwait(
                        ValidationErrorResponse(
                            violations.associate { it.propertyPath.toString() to it.message },
                        ),
                    )
            } else {
                val tokenPair = authService.refresh(body.refreshToken)
                ServerResponse.ok().bodyValueAndAwait(AuthResponse(tokenPair.accessToken, tokenPair.refreshToken))
            }
        } catch (_: InvalidRefreshTokenException) {
            ServerResponse.status(HttpStatus.UNAUTHORIZED).bodyValueAndAwait(ErrorResponse("Invalid refresh token"))
        }
    }
}
