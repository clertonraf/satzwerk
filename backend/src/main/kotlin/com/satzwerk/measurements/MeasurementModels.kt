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
    val shoulders: BigDecimal? = null,
    @field:DecimalMin("0.01")
    val chest: BigDecimal? = null,
    @field:DecimalMin("0.01")
    val weightKg: BigDecimal? = null,
    @field:DecimalMin("0.01")
    val rightBicep: BigDecimal? = null,
    @field:DecimalMin("0.01")
    val leftBicep: BigDecimal? = null,
    @field:DecimalMin("0.01")
    val rightForearm: BigDecimal? = null,
    @field:DecimalMin("0.01")
    val leftForearm: BigDecimal? = null,
    @field:DecimalMin("0.01")
    val abdomen: BigDecimal? = null,
    @field:DecimalMin("0.01")
    val glutes: BigDecimal? = null,
    @field:DecimalMin("0.01")
    val rightThigh: BigDecimal? = null,
    @field:DecimalMin("0.01")
    val leftThigh: BigDecimal? = null,
    @field:DecimalMin("0.01")
    val rightCalf: BigDecimal? = null,
    @field:DecimalMin("0.01")
    val leftCalf: BigDecimal? = null,
)

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

fun BodyMeasurement.toResponse(): MeasurementResponse =
    MeasurementResponse(
        id = requireNotNull(id),
        measurementDate = measurementDate,
        shoulders = shoulders,
        chest = chest,
        weightKg = weightKg,
        rightBicep = rightBicep,
        leftBicep = leftBicep,
        rightForearm = rightForearm,
        leftForearm = leftForearm,
        abdomen = abdomen,
        glutes = glutes,
        rightThigh = rightThigh,
        leftThigh = leftThigh,
        rightCalf = rightCalf,
        leftCalf = leftCalf,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
