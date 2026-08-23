package com.satzwerk.partners

import com.satzwerk.common.BadRequestException
import com.satzwerk.common.ConflictException
import com.satzwerk.common.ForbiddenException
import com.satzwerk.common.NotFoundException
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.UUID

private val ALLOWED_SCOPES =
    setOf(
        "exercises:read", "exercises:write",
        "plans:read", "plans:write",
        "sessions:read", "sessions:write",
        "analytics:read",
        "measurements:read", "measurements:write",
        "medications:read", "medications:write",
    )

@Service
class PartnerAppService(
    private val partnerAppRepository: PartnerAppRepository,
    private val appGrantRepository: AppGrantRepository,
    private val passwordEncoder: PasswordEncoder,
) {
    /** Register a new partner app. Returns the plain client secret once — never retrievable again. */
    suspend fun registerApp(request: RegisterPartnerAppRequest): PartnerAppRegistrationResponse {
        validateScopes(request.scopes)
        val clientId = "satzwerk_${UUID.randomUUID().toString().replace("-", "")}"
        val plainSecret = generateSecret()
        val app =
            partnerAppRepository.save(
                PartnerApp(
                    name = request.name,
                    description = request.description,
                    redirectUri = request.redirectUri,
                    clientId = clientId,
                    clientSecretHash = passwordEncoder.encode(plainSecret),
                    scopes = request.scopes.normaliseScopes(),
                ),
            )
        return PartnerAppRegistrationResponse(
            id = requireNotNull(app.id),
            name = app.name,
            description = app.description,
            redirectUri = app.redirectUri,
            clientId = app.clientId,
            clientSecret = plainSecret,
            scopes = app.scopes,
            createdAt = app.createdAt,
        )
    }

    /** List all registered partner apps (admin / developer-facing). */
    suspend fun listApps(): List<PartnerAppSummary> = partnerAppRepository.findAll().map { it.toSummary() }.toList()

    /**
     * Grant a user's consent to a partner app, issuing an opaque access token.
     * Returns the plain token once — it is not recoverable after this call.
     */
    suspend fun grantAccess(
        userId: UUID,
        request: GrantAppAccessRequest,
    ): AppGrantResponse {
        val app =
            partnerAppRepository.findByClientId(request.clientId)
                ?: throw NotFoundException("Partner app not found: ${request.clientId}")

        val grantedScopes = request.grantedScopes.normaliseScopes()
        validateScopesSubset(grantedScopes, app.scopes)

        val existing = appGrantRepository.findByAppIdAndUserId(requireNotNull(app.id), userId)
        if (existing != null && existing.revokedAt == null) {
            throw ConflictException("Access to app '${app.name}' already granted")
        }

        val plainToken = generateSecret()
        val grant =
            appGrantRepository.save(
                AppGrant(
                    appId = requireNotNull(app.id),
                    userId = userId,
                    grantedScopes = grantedScopes,
                    accessTokenHash = sha256(plainToken),
                    // If re-granting after revocation, replace existing row
                    id = existing?.id,
                    grantedAt = Instant.now(),
                    revokedAt = null,
                    revokedBy = null,
                ),
            )
        return AppGrantResponse(
            grantId = requireNotNull(grant.id),
            appId = app.id,
            appName = app.name,
            grantedScopes = grant.grantedScopes,
            accessToken = plainToken,
            grantedAt = grant.grantedAt,
        )
    }

    /** List active (non-revoked) grants for a user — shown on the Connected Apps settings surface. */
    suspend fun listActiveGrants(userId: UUID): List<ActiveGrantResponse> {
        val grants = appGrantRepository.findAllByUserId(userId).toList()
        val activeGrants = grants.filter { it.revokedAt == null }
        val appIds = activeGrants.map { it.appId }.toSet()
        val appsById =
            partnerAppRepository
                .findAllById(appIds)
                .toList()
                .associateBy { requireNotNull(it.id) }
        return activeGrants.map { grant ->
            val app = appsById[grant.appId]
            ActiveGrantResponse(
                grantId = requireNotNull(grant.id),
                appId = grant.appId,
                appName = app?.name ?: "Unknown App",
                grantedScopes = grant.grantedScopes,
                grantedAt = grant.grantedAt,
            )
        }
    }

    /**
     * Revoke a user's grant to a partner app immediately.
     * Revocation is audited with timestamp and actor ('user').
     */
    suspend fun revokeGrant(
        userId: UUID,
        grantId: UUID,
    ) {
        val grant =
            appGrantRepository.findById(grantId)
                ?: throw NotFoundException("Grant not found")
        requireGrantOwnership(grant, userId)
        appGrantRepository.save(
            grant.copy(
                revokedAt = Instant.now(),
                revokedBy = "user",
            ),
        )
    }

    /**
     * Resolve an opaque app access token to an active grant.
     * Returns null if the token is unknown or the grant has been revoked.
     * Used by [com.satzwerk.config.PartnerTokenWebFilter] to authenticate partner requests.
     */
    suspend fun resolveActiveGrant(plainToken: String): AppGrant? =
        appGrantRepository
            .findByAccessTokenHash(sha256(plainToken))
            ?.takeIf { it.revokedAt == null }

    /**
     * Build the [PartnerGrantBinding] for an already-resolved active grant.
     * Used by `GET /api/partner-grants/me` — the partner-token-accessible read probe.
     * Throws [NotFoundException] if the referenced app has been deleted.
     */
    suspend fun resolveBinding(grant: AppGrant): PartnerGrantBinding {
        val app =
            partnerAppRepository.findById(requireNotNull(grant.appId))
                ?: throw NotFoundException("Partner app not found for grant")
        return PartnerGrantBinding(
            grantId = requireNotNull(grant.id),
            appId = requireNotNull(app.id),
            appName = app.name,
            userId = grant.userId,
            grantedScopes = grant.grantedScopes,
            grantedAt = grant.grantedAt,
        )
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private fun validateScopes(scopes: String) {
        val invalid = scopes.normaliseScopes().split(" ").filter { it !in ALLOWED_SCOPES }
        if (invalid.isNotEmpty()) {
            throw BadRequestException("Unknown scopes: ${invalid.joinToString()}")
        }
    }

    private fun validateScopesSubset(
        grantedScopes: String,
        appScopes: String,
    ) {
        val appScopeSet = appScopes.split(" ").toSet()
        val invalid = grantedScopes.split(" ").filter { it !in appScopeSet }
        if (invalid.isNotEmpty()) {
            throw BadRequestException("Scopes not declared by app: ${invalid.joinToString()}")
        }
    }

    private fun PartnerApp.toSummary() =
        PartnerAppSummary(
            id = requireNotNull(id),
            name = name,
            description = description,
            redirectUri = redirectUri,
            clientId = clientId,
            scopes = scopes,
            createdAt = createdAt,
        )
}

/** Normalise scopes: lowercase, trim, deduplicate, sort for canonical storage. */
internal fun String.normaliseScopes(): String =
    split(" ", ",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct().sorted().joinToString(" ")

private const val SECRET_BYTES = 32

private fun generateSecret(): String {
    val bytes = ByteArray(SECRET_BYTES)
    SecureRandom().nextBytes(bytes)
    return bytes.joinToString("") { "%02x".format(it) }
}

internal fun sha256(raw: String): String =
    MessageDigest
        .getInstance("SHA-256")
        .digest(raw.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

/**
 * Package-level guard: throws [ForbiddenException] if [grant] does not belong to [userId],
 * or [BadRequestException] if it has already been revoked.
 * Extracted to keep [PartnerAppService] under the detekt TooManyFunctions threshold.
 */
internal fun requireGrantOwnership(
    grant: AppGrant,
    userId: UUID,
) {
    if (grant.userId != userId) throw ForbiddenException("Grant does not belong to this user")
    if (grant.revokedAt != null) throw BadRequestException("Grant already revoked")
}
