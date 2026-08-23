package com.satzwerk.partners

import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import java.util.UUID

interface AppGrantRepository : CoroutineCrudRepository<AppGrant, UUID> {
    suspend fun findByAccessTokenHash(accessTokenHash: String): AppGrant?

    suspend fun findByAppIdAndUserId(
        appId: UUID,
        userId: UUID,
    ): AppGrant?

    fun findAllByUserId(userId: UUID): kotlinx.coroutines.flow.Flow<AppGrant>
}
