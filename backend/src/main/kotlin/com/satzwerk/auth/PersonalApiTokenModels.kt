package com.satzwerk.auth

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

/** Valid scope strings for personal automation tokens. */
object TokenScope {
    const val EXERCISES_READ = "exercises:read"
    const val EXERCISES_WRITE = "exercises:write"
    const val PLANS_READ = "plans:read"
    const val PLANS_WRITE = "plans:write"
    const val SESSIONS_READ = "sessions:read"
    const val SESSIONS_WRITE = "sessions:write"
    const val ANALYTICS_READ = "analytics:read"
    const val MEASUREMENTS_READ = "measurements:read"
    const val MEASUREMENTS_WRITE = "measurements:write"
    const val MEDICATIONS_READ = "medications:read"
    const val MEDICATIONS_WRITE = "medications:write"

    val all =
        setOf(
            EXERCISES_READ, EXERCISES_WRITE,
            PLANS_READ, PLANS_WRITE,
            SESSIONS_READ, SESSIONS_WRITE,
            ANALYTICS_READ,
            MEASUREMENTS_READ, MEASUREMENTS_WRITE,
            MEDICATIONS_READ, MEDICATIONS_WRITE,
        )
}

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
