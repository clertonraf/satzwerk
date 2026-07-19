package com.satzwerk.medications

import io.r2dbc.postgresql.codec.Json
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Table("medications")
data class Medication(
    @Id val id: UUID? = null,
    @Column("user_id") val userId: UUID,
    val name: String,
    @Column("dosage_amount") val dosageAmount: BigDecimal,
    @Column("dosage_unit") val dosageUnit: String,
    val frequency: Json,
    val purpose: String? = null,
    @Column("is_active") val isActive: Boolean = true,
    @Column("created_at") val createdAt: Instant = Instant.now(),
    @Column("updated_at") val updatedAt: Instant = Instant.now(),
)
