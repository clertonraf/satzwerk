package com.satzwerk.measurements

import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import java.time.LocalDate
import java.util.UUID

interface MeasurementRepository : CoroutineCrudRepository<BodyMeasurement, UUID> {
    suspend fun findByUserIdOrderByMeasurementDateDesc(userId: UUID): List<BodyMeasurement>

    suspend fun findByUserIdAndMeasurementDate(
        userId: UUID,
        measurementDate: LocalDate,
    ): BodyMeasurement?

    suspend fun deleteByUserIdAndMeasurementDate(
        userId: UUID,
        measurementDate: LocalDate,
    )
}
