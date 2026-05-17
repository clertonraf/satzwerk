package com.satzwerk.auth

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

@Table("refresh_tokens")
data class RefreshToken(
    @Id
    val id: UUID? = null,
    @Column("user_id")
    val userId: UUID,
    @Column("token_hash")
    val tokenHash: String,
    @Column("expires_at")
    val expiresAt: Instant,
    @Column("revoked_at")
    val revokedAt: Instant? = null,
    @Column("created_at")
    val createdAt: Instant = Instant.now(),
)
