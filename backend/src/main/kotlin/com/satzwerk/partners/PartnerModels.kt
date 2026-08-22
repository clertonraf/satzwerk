package com.satzwerk.partners

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

// ── Request models ──────────────────────────────────────────────────────────

data class RegisterPartnerAppRequest(
    @field:NotBlank
    @field:Size(max = 100)
    val name: String,
    @field:NotBlank
    @field:Size(max = 500)
    val description: String,
    @field:NotBlank
    val redirectUri: String,
    /**
     * Space-separated scopes from the allowed set defined in ADR-0005.
     * Example: "exercises:read plans:read sessions:read"
     */
    @field:NotBlank
    val scopes: String,
)

data class GrantAppAccessRequest(
    @field:NotBlank
    val clientId: String,
    /**
     * Space-separated scopes the user explicitly consents to.
     * Must be a subset of the app's declared scopes.
     */
    @field:NotBlank
    val grantedScopes: String,
)

// ── Response models ─────────────────────────────────────────────────────────

data class PartnerAppRegistrationResponse(
    val id: UUID,
    val name: String,
    val description: String,
    val redirectUri: String,
    val clientId: String,
    /** Returned once at registration time only — never retrievable again. */
    val clientSecret: String,
    val scopes: String,
    val createdAt: Instant,
)

data class PartnerAppSummary(
    val id: UUID,
    val name: String,
    val description: String,
    val redirectUri: String,
    val clientId: String,
    val scopes: String,
    val createdAt: Instant,
)

data class AppGrantResponse(
    val grantId: UUID,
    val appId: UUID,
    val appName: String,
    val grantedScopes: String,
    /** Opaque bearer token for the partner app — returned once at grant time only. */
    val accessToken: String,
    val grantedAt: Instant,
)

data class ActiveGrantResponse(
    val grantId: UUID,
    val appId: UUID,
    val appName: String,
    val grantedScopes: String,
    val grantedAt: Instant,
)

/**
 * Returned by the partner-token-accessible probe `GET /api/partner-grants/me`.
 * Proves the credential is active and shows the bound app+user identity without
 * exposing any management capability.
 */
data class PartnerGrantBinding(
    val grantId: UUID,
    val appId: UUID,
    val appName: String,
    val userId: UUID,
    val grantedScopes: String,
    val grantedAt: Instant,
)
