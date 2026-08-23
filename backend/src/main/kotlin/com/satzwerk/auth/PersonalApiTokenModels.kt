package com.satzwerk.auth

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class CreatePersonalApiTokenRequest(
    @field:NotBlank
    @field:Size(max = 100)
    val name: String,
    @field:Size(min = 1, message = "at least one scope is required")
    val scopes: List<String>,
)

data class PersonalApiTokenResponse(
    val id: UUID,
    val name: String,
    val scopes: List<String>,
    val createdAt: Instant,
    val lastUsedAt: Instant? = null,
)

/** Returned only once at creation; the raw token is never stored. */
data class CreatedPersonalApiTokenResponse(
    val id: UUID,
    val name: String,
    val scopes: List<String>,
    val createdAt: Instant,
    val token: String,
)

class InsufficientScopeException(scope: String) : RuntimeException("Required scope: $scope")

class TokenRevokedException : RuntimeException("Token has been revoked")
