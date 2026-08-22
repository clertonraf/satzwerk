package com.satzwerk.auth

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

/**
 * Personal automation token for a Satzwerk user.
 *
 * - [tokenHash] stores SHA-256 of the raw token; the raw value is returned only at creation.
 * - [scopesRaw] stores a comma-separated list of granted scope strings (e.g. "analytics:read,exercises:read").
 * - A token is considered active when [revokedAt] is null.
 */
@Table("personal_api_tokens")
data class PersonalApiToken(
    @Id
    val id: UUID? = null,
    @Column("user_id")
    val userId: UUID,
    val name: String,
    @Column("token_hash")
    val tokenHash: String,
    @Column("scopes")
    val scopesRaw: String,
    @Column("created_at")
    val createdAt: Instant = Instant.now(),
    @Column("last_used_at")
    val lastUsedAt: Instant? = null,
    @Column("revoked_at")
    val revokedAt: Instant? = null,
) {
    fun scopes(): List<String> = scopesRaw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
}
