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
    override val shoulders: BigDecimal? = null,
    override val chest: BigDecimal? = null,
    @Column("weight_kg") override val weightKg: BigDecimal? = null,
    @Column("right_bicep") override val rightBicep: BigDecimal? = null,
    @Column("left_bicep") override val leftBicep: BigDecimal? = null,
    @Column("right_forearm") override val rightForearm: BigDecimal? = null,
    @Column("left_forearm") override val leftForearm: BigDecimal? = null,
    override val abdomen: BigDecimal? = null,
    override val glutes: BigDecimal? = null,
    @Column("right_thigh") override val rightThigh: BigDecimal? = null,
    @Column("left_thigh") override val leftThigh: BigDecimal? = null,
    @Column("right_calf") override val rightCalf: BigDecimal? = null,
    @Column("left_calf") override val leftCalf: BigDecimal? = null,
    @Column("created_at") val createdAt: Instant = Instant.now(),
    @Column("updated_at") val updatedAt: Instant = Instant.now(),
) : BodyMeasurementFieldSource
