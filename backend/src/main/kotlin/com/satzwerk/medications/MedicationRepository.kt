package com.satzwerk.medications

import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import java.util.UUID

interface MedicationRepository : CoroutineCrudRepository<Medication, UUID> {
    suspend fun findByUserIdOrderByNameAsc(userId: UUID): List<Medication>

    suspend fun findByUserIdAndId(
        userId: UUID,
        id: UUID,
    ): Medication?

    suspend fun findByUserIdAndNameIgnoreCase(
        userId: UUID,
        name: String,
    ): Medication?
}
