package com.satzwerk.auth

import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import java.time.Instant
import java.util.UUID

interface RefreshTokenRepository : CoroutineCrudRepository<RefreshToken, UUID> {
    suspend fun findByTokenHash(tokenHash: String): RefreshToken?

    suspend fun deleteByUserId(userId: UUID)

    suspend fun deleteByExpiresAtBefore(cutoff: Instant)

    suspend fun deleteByRevokedAtIsNotNullAndRevokedAtBefore(cutoff: Instant)
}
