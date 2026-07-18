package com.satzwerk.measurements

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Table("body_measurements")
data class BodyMeasurement(
    @Id val id: UUID? = null,
    @Column("user_id") val userId: UUID,
    @Column("measurement_date") val measurementDate: LocalDate,
    val shoulders: BigDecimal? = null,
    val chest: BigDecimal? = null,
    @Column("weight_kg") val weightKg: BigDecimal? = null,
    @Column("right_bicep") val rightBicep: BigDecimal? = null,
    @Column("left_bicep") val leftBicep: BigDecimal? = null,
    @Column("right_forearm") val rightForearm: BigDecimal? = null,
    @Column("left_forearm") val leftForearm: BigDecimal? = null,
    val abdomen: BigDecimal? = null,
    val glutes: BigDecimal? = null,
    @Column("right_thigh") val rightThigh: BigDecimal? = null,
    @Column("left_thigh") val leftThigh: BigDecimal? = null,
    @Column("right_calf") val rightCalf: BigDecimal? = null,
    @Column("left_calf") val leftCalf: BigDecimal? = null,
    @Column("created_at") val createdAt: Instant = Instant.now(),
    @Column("updated_at") val updatedAt: Instant = Instant.now(),
)
