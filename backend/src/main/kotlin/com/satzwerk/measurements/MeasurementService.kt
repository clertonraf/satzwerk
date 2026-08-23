package com.satzwerk.measurements

import com.satzwerk.common.NotFoundException
import org.springframework.stereotype.Service
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
