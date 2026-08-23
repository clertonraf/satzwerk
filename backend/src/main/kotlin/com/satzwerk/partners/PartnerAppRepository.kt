package com.satzwerk.partners

import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import java.util.UUID

interface PartnerAppRepository : CoroutineCrudRepository<PartnerApp, UUID> {
    suspend fun findByClientId(clientId: String): PartnerApp?
}
