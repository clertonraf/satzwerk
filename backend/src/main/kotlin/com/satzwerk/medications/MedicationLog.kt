package com.satzwerk.medications

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Table("medication_logs")
data class MedicationLog(
    @Id val id: UUID? = null,
    @Column("medication_id") val medicationId: UUID,
    @Column("user_id") val userId: UUID,
    @Column("taken_at") val takenAt: Instant,
    val taken: Boolean,
    @Column("dose_amount") val doseAmount: BigDecimal? = null,
    val notes: String? = null,
    @Column("created_at") val createdAt: Instant = Instant.now(),
)
