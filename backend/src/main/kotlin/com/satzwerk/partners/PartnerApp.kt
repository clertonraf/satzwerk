package com.satzwerk.partners

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

/**
 * A third-party application registered with Satzwerk to consume the public API.
 * Scopes are stored as a space-separated string (e.g. "exercises:read plans:read").
 */
@Table("partner_apps")
data class PartnerApp(
    @Id
    val id: UUID? = null,
    val name: String,
    val description: String,
    @Column("redirect_uri")
    val redirectUri: String,
    @Column("client_id")
    val clientId: String,
    /** bcrypt hash of the issued client secret; never stored in plaintext. */
    @Column("client_secret_hash")
    val clientSecretHash: String,
    /** Space-separated declared scopes. */
    val scopes: String,
    @Column("created_at")
    val createdAt: Instant = Instant.now(),
)
