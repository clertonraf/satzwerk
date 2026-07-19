package com.satzwerk.medications

import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import java.time.Instant
import java.util.UUID

interface MedicationLogRepository : CoroutineCrudRepository<MedicationLog, UUID> {
    suspend fun findByMedicationIdAndTakenAtBetweenOrderByTakenAtDesc(
        medicationId: UUID,
        from: Instant,
        to: Instant,
    ): List<MedicationLog>

    suspend fun findByUserIdAndTakenAtBetweenOrderByTakenAtDesc(
        userId: UUID,
        from: Instant,
        to: Instant,
    ): List<MedicationLog>

    suspend fun findByMedicationIdInAndTakenAtBetweenOrderByTakenAtDesc(
        medicationIds: List<UUID>,
        from: Instant,
        to: Instant,
    ): List<MedicationLog>
}
