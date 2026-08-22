package com.satzwerk.auth

import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import java.time.Instant
import java.util.UUID

interface PersonalApiTokenRepository : CoroutineCrudRepository<PersonalApiToken, UUID> {
    suspend fun findByTokenHash(tokenHash: String): PersonalApiToken?

    suspend fun findByUserIdAndRevokedAtIsNull(userId: UUID): List<PersonalApiToken>

    suspend fun findByIdAndUserId(
        id: UUID,
        userId: UUID,
    ): PersonalApiToken?

    suspend fun deleteByUserId(userId: UUID)

    suspend fun deleteByRevokedAtIsNotNullAndRevokedAtBefore(cutoff: Instant)
}
