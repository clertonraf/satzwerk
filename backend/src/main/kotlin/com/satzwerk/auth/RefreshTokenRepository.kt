package com.satzwerk.auth

import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import java.util.UUID

interface RefreshTokenRepository : CoroutineCrudRepository<RefreshToken, UUID> {
    suspend fun findByTokenHash(tokenHash: String): RefreshToken?

    suspend fun deleteByUserId(userId: UUID)
}
