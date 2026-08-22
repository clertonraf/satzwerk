package com.satzwerk.partners

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

/**
 * Records a user's explicit consent to a PartnerApp for a set of scopes.
 *
 * Revocation is immediate: setting [revokedAt] to a non-null value blocks any
 * further API calls that present the associated [accessTokenHash].
 */
@Table("app_grants")
data class AppGrant(
    @Id
    val id: UUID? = null,
    @Column("app_id")
    val appId: UUID,
    @Column("user_id")
    val userId: UUID,
    /** Space-separated scopes the user explicitly granted. */
    @Column("granted_scopes")
    val grantedScopes: String,
    /** SHA-256 hex of the opaque access token issued to the partner app. */
    @Column("access_token_hash")
    val accessTokenHash: String,
    @Column("granted_at")
    val grantedAt: Instant = Instant.now(),
    /** NULL = active grant; non-NULL = revoked. Revocation is immediate. */
    @Column("revoked_at")
    val revokedAt: Instant? = null,
    /** 'user' or 'admin' — who initiated revocation. */
    @Column("revoked_by")
    val revokedBy: String? = null,
)
