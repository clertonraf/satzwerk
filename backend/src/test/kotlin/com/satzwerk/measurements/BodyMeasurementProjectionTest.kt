package com.satzwerk.measurements

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class BodyMeasurementProjectionTest {
    @Test
    fun `mergeWith preserves existing null fields and keeps measurementDate semantics`() {
        val createdAt = Instant.parse("2026-01-15T08:00:00Z")
        val existing =
            BodyMeasurement(
                id = UUID.randomUUID(),
                userId = UUID.randomUUID(),
                measurementDate = LocalDate.of(2026, 1, 15),
                shoulders = BigDecimal("120.50"),
                chest = BigDecimal("100.00"),
                createdAt = createdAt,
                updatedAt = createdAt,
            )
        val request =
            UpsertMeasurementRequest(
                measurementDate = existing.measurementDate,
                weightKg = BigDecimal("82.30"),
            )
        val merged = existing.mergeWith(request, updatedAt = Instant.parse("2026-01-15T09:00:00Z"))

        assertEquals(existing.id, merged.id)
        assertEquals(existing.userId, merged.userId)
        assertEquals(existing.measurementDate, merged.measurementDate)
        assertEquals(BigDecimal("120.50"), merged.shoulders)
        assertEquals(BigDecimal("100.00"), merged.chest)
        assertEquals(BigDecimal("82.30"), merged.weightKg)
        assertEquals(createdAt, merged.createdAt)
        assertEquals(Instant.parse("2026-01-15T09:00:00Z"), merged.updatedAt)
    }
}
