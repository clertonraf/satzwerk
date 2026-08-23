package com.satzwerk.measurements

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

internal interface BodyMeasurementFieldSource {
    val shoulders: BigDecimal?
    val chest: BigDecimal?
    val weightKg: BigDecimal?
    val rightBicep: BigDecimal?
    val leftBicep: BigDecimal?
    val rightForearm: BigDecimal?
    val leftForearm: BigDecimal?
    val abdomen: BigDecimal?
    val glutes: BigDecimal?
    val rightThigh: BigDecimal?
    val leftThigh: BigDecimal?
    val rightCalf: BigDecimal?
    val leftCalf: BigDecimal?
}

internal data class BodyMeasurementProjection(
    val shoulders: BigDecimal? = null,
    val chest: BigDecimal? = null,
    val weightKg: BigDecimal? = null,
    val rightBicep: BigDecimal? = null,
    val leftBicep: BigDecimal? = null,
    val rightForearm: BigDecimal? = null,
    val leftForearm: BigDecimal? = null,
    val abdomen: BigDecimal? = null,
    val glutes: BigDecimal? = null,
    val rightThigh: BigDecimal? = null,
    val leftThigh: BigDecimal? = null,
    val rightCalf: BigDecimal? = null,
    val leftCalf: BigDecimal? = null,
)

internal fun BodyMeasurementFieldSource.toProjection(): BodyMeasurementProjection =
    BodyMeasurementProjection(
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
    )

internal fun BodyMeasurementProjection.merge(overrides: BodyMeasurementProjection): BodyMeasurementProjection =
    BodyMeasurementProjection(
        shoulders = overrides.shoulders ?: shoulders,
        chest = overrides.chest ?: chest,
        weightKg = overrides.weightKg ?: weightKg,
        rightBicep = overrides.rightBicep ?: rightBicep,
        leftBicep = overrides.leftBicep ?: leftBicep,
        rightForearm = overrides.rightForearm ?: rightForearm,
        leftForearm = overrides.leftForearm ?: leftForearm,
        abdomen = overrides.abdomen ?: abdomen,
        glutes = overrides.glutes ?: glutes,
        rightThigh = overrides.rightThigh ?: rightThigh,
        leftThigh = overrides.leftThigh ?: leftThigh,
        rightCalf = overrides.rightCalf ?: rightCalf,
        leftCalf = overrides.leftCalf ?: leftCalf,
    )

internal fun BodyMeasurementProjection.toEntity(
    userId: UUID,
    measurementDate: LocalDate,
    id: UUID? = null,
    createdAt: Instant = Instant.now(),
    updatedAt: Instant = createdAt,
): BodyMeasurement =
    BodyMeasurement(
        id = id,
        userId = userId,
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

internal fun UpsertMeasurementRequest.toEntity(userId: UUID): BodyMeasurement =
    toProjection().toEntity(userId = userId, measurementDate = measurementDate)

internal fun BodyMeasurement.mergeWith(
    request: UpsertMeasurementRequest,
    updatedAt: Instant = Instant.now(),
): BodyMeasurement =
    toProjection()
        .merge(request.toProjection())
        .toEntity(
            id = id,
            userId = userId,
            measurementDate = measurementDate,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

internal fun BodyMeasurement.toResponse(): MeasurementResponse =
    toProjection().toResponse(
        id = requireNotNull(id),
        measurementDate = measurementDate,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

private fun BodyMeasurementProjection.toResponse(
    id: UUID,
    measurementDate: LocalDate,
    createdAt: Instant,
    updatedAt: Instant,
): MeasurementResponse =
    MeasurementResponse(
        id = id,
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
