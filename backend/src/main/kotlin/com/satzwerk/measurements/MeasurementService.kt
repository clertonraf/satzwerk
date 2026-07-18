package com.satzwerk.measurements

import com.satzwerk.common.NotFoundException
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Service
class MeasurementService(
    private val measurementRepository: MeasurementRepository,
) {
    suspend fun upsert(
        userId: UUID,
        request: UpsertMeasurementRequest,
    ): MeasurementResponse {
        val existing = measurementRepository.findByUserIdAndMeasurementDate(userId, request.measurementDate)
        val entity =
            if (existing != null) {
                existing.mergeWith(request)
            } else {
                request.toEntity(userId)
            }
        return measurementRepository.save(entity).toResponse()
    }

    suspend fun findAll(userId: UUID): List<MeasurementResponse> =
        measurementRepository.findByUserIdOrderByMeasurementDateDesc(userId).map { it.toResponse() }

    suspend fun deleteByDate(
        userId: UUID,
        measurementDate: LocalDate,
    ) {
        measurementRepository.findByUserIdAndMeasurementDate(userId, measurementDate)
            ?: throw NotFoundException("Measurement not found for date $measurementDate")
        measurementRepository.deleteByUserIdAndMeasurementDate(userId, measurementDate)
    }
}

// Package-level helpers keep MeasurementService under the TooManyFunctions threshold
// and distribute cyclomatic complexity away from upsert.

private fun BodyMeasurement.mergeWith(request: UpsertMeasurementRequest): BodyMeasurement =
    copy(
        shoulders = request.shoulders ?: shoulders,
        chest = request.chest ?: chest,
        weightKg = request.weightKg ?: weightKg,
        rightBicep = request.rightBicep ?: rightBicep,
        leftBicep = request.leftBicep ?: leftBicep,
        rightForearm = request.rightForearm ?: rightForearm,
        leftForearm = request.leftForearm ?: leftForearm,
        abdomen = request.abdomen ?: abdomen,
        glutes = request.glutes ?: glutes,
        rightThigh = request.rightThigh ?: rightThigh,
        leftThigh = request.leftThigh ?: leftThigh,
        rightCalf = request.rightCalf ?: rightCalf,
        leftCalf = request.leftCalf ?: leftCalf,
        updatedAt = Instant.now(),
    )

private fun UpsertMeasurementRequest.toEntity(userId: UUID): BodyMeasurement =
    BodyMeasurement(
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
    )
