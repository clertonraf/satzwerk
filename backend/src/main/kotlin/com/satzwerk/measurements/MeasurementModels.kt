package com.satzwerk.measurements

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class UpsertMeasurementRequest(
    @field:NotNull
    val measurementDate: LocalDate,
    @field:DecimalMin("0.01")
    override val shoulders: BigDecimal? = null,
    @field:DecimalMin("0.01")
    override val chest: BigDecimal? = null,
    @field:DecimalMin("0.01")
    override val weightKg: BigDecimal? = null,
    @field:DecimalMin("0.01")
    override val rightBicep: BigDecimal? = null,
    @field:DecimalMin("0.01")
    override val leftBicep: BigDecimal? = null,
    @field:DecimalMin("0.01")
    override val rightForearm: BigDecimal? = null,
    @field:DecimalMin("0.01")
    override val leftForearm: BigDecimal? = null,
    @field:DecimalMin("0.01")
    override val abdomen: BigDecimal? = null,
    @field:DecimalMin("0.01")
    override val glutes: BigDecimal? = null,
    @field:DecimalMin("0.01")
    override val rightThigh: BigDecimal? = null,
    @field:DecimalMin("0.01")
    override val leftThigh: BigDecimal? = null,
    @field:DecimalMin("0.01")
    override val rightCalf: BigDecimal? = null,
    @field:DecimalMin("0.01")
    override val leftCalf: BigDecimal? = null,
) : BodyMeasurementFieldSource

data class MeasurementResponse(
    val id: UUID,
    val measurementDate: LocalDate,
    val shoulders: BigDecimal?,
    val chest: BigDecimal?,
    val weightKg: BigDecimal?,
    val rightBicep: BigDecimal?,
    val leftBicep: BigDecimal?,
    val rightForearm: BigDecimal?,
    val leftForearm: BigDecimal?,
    val abdomen: BigDecimal?,
    val glutes: BigDecimal?,
    val rightThigh: BigDecimal?,
    val leftThigh: BigDecimal?,
    val rightCalf: BigDecimal?,
    val leftCalf: BigDecimal?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
