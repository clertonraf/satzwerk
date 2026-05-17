package com.satzwerk.auth

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class RegisterRequest(
    @field:Email
    val email: String,
    @field:Size(min = 8)
    val password: String,
    @field:NotBlank
    val displayName: String,
)

data class LoginRequest(
    @field:Email
    val email: String,
    @field:NotBlank
    val password: String,
)

data class RefreshRequest(
    @field:NotBlank
    val refreshToken: String,
)

data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
)

data class TokenPair(
    val accessToken: String,
    val refreshToken: String,
)

class DuplicateEmailException(email: String) : RuntimeException("User already exists for email: $email")

class InvalidCredentialsException : RuntimeException("Invalid email or password")

class InvalidRefreshTokenException : RuntimeException("Invalid refresh token")
