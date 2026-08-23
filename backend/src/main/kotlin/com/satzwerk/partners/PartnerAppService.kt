package com.satzwerk.partners

import com.satzwerk.auth.TokenSecretService
import com.satzwerk.common.BadRequestException
import com.satzwerk.common.ConflictException
import com.satzwerk.common.NotFoundException
import com.satzwerk.common.Owned
import com.satzwerk.common.assertOwner
import com.satzwerk.publicapi.validateDeclaredPublicScopes
import com.satzwerk.publicapi.validateGrantedPublicScopes
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class PartnerAppService(
    private val partnerAppRepository: PartnerAppRepository,
    private val appGrantRepository: AppGrantRepository,
    private val passwordEncoder: PasswordEncoder,
    private val tokenSecretService: TokenSecretService,
) {
    /** Register a new partner app. Returns the plain client secret once — never retrievable again. */
    suspend fun registerApp(request: RegisterPartnerAppRequest): PartnerAppRegistrationResponse {
        val declaredScopes = validateScopes(request.scopes)
        val clientId = "satzwerk_${UUID.randomUUID().toString().replace("-", "")}"
        val plainSecret = tokenSecretService.generateHexToken(SECRET_BYTES)
        val app =
            partnerAppRepository.save(
                PartnerApp(
                    name = request.name,
                    description = request.description,
                    redirectUri = request.redirectUri,
                    clientId = clientId,
                    clientSecretHash = passwordEncoder.encode(plainSecret),
                    scopes = declaredScopes,
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

        val grantedScopes = validateScopesSubset(request.grantedScopes, app.scopes)

        val existing = appGrantRepository.findByAppIdAndUserId(requireNotNull(app.id), userId)
        if (existing != null && existing.revokedAt == null) {
            throw ConflictException("Access to app '${app.name}' already granted")
        }

        val plainToken = tokenSecretService.generateHexToken(SECRET_BYTES)
        val grant =
            appGrantRepository.save(
                AppGrant(
                    appId = requireNotNull(app.id),
                    userId = userId,
                    grantedScopes = grantedScopes,
                    accessTokenHash = tokenSecretService.hash(plainToken),
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
        val ownedGrant = requireGrantOwnership(grant, userId)
        appGrantRepository.save(
            ownedGrant.copy(
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
            .findByAccessTokenHash(tokenSecretService.hash(plainToken))
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

    private fun validateScopes(scopes: String): String = validateDeclaredPublicScopes(scopes)

    private fun validateScopesSubset(
        grantedScopes: String,
        appScopes: String,
    ): String = validateGrantedPublicScopes(grantedScopes, appScopes)

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

private const val SECRET_BYTES = 32

/**
 * Package-level guard: throws [ForbiddenException] if [grant] does not belong to [userId],
 * or [BadRequestException] if it has already been revoked.
 * Extracted to keep [PartnerAppService] under the detekt TooManyFunctions threshold.
 */
internal fun requireGrantOwnership(
    grant: AppGrant,
    userId: UUID,
): AppGrant {
    val ownedGrant = Owned(grant, grant.userId).assertOwner(userId, "Grant").value
    if (ownedGrant.revokedAt != null) throw BadRequestException("Grant already revoked")
    return ownedGrant
}
