package com.satzwerk.medications

import com.fasterxml.jackson.databind.ObjectMapper
import io.r2dbc.postgresql.codec.Json
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

class MedicationAnalyticsServiceTest {
    private val objectMapper = ObjectMapper().findAndRegisterModules()
    private val userId = UUID.randomUUID()
    private val medicationId = UUID.randomUUID()

    private val today = LocalDate.now(ZoneOffset.UTC)

    private fun takenLogAt(date: LocalDate): MedicationLog =
        MedicationLog(
            id = UUID.randomUUID(),
            medicationId = medicationId,
            userId = userId,
            takenAt = date.atStartOfDay(ZoneOffset.UTC).plusHours(8).toInstant(),
            taken = true,
        )

    private fun dailyMedication(): Medication =
        Medication(
            id = medicationId,
            userId = userId,
            name = "Vitamin D",
            dosageAmount = BigDecimal("1000.0000"),
            dosageUnit = DosageUnit.IU.name,
            frequency = Json.of("""{"type":"DAILY","timesPerDay":1,"times":[]}"""),
            isActive = true,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )

    @Test
    fun `getAdherenceStreak returns 0 when medication not found`(): Unit =
        runBlocking {
            val medRepo: MedicationRepository = mock { onBlocking { findById(any()) } doReturn null }
            val logRepo: MedicationLogRepository = mock()
            val service = MedicationAnalyticsService(medRepo, logRepo, objectMapper)

            assertEquals(0, service.getAdherenceStreak(medicationId))
        }

    @Test
    fun `getAdherenceStreak counts consecutive days ending today`(): Unit =
        runBlocking {
            val logs =
                listOf(
                    takenLogAt(today),
                    takenLogAt(today.minusDays(1)),
                    takenLogAt(today.minusDays(2)),
                )
            val logRepo: MedicationLogRepository =
                mock {
                    onBlocking {
                        findByMedicationIdAndTakenAtBetweenOrderByTakenAtDesc(any(), any(), any())
                    } doReturn logs
                }
            val medRepo: MedicationRepository =
                mock { onBlocking { findById(any()) } doReturn dailyMedication() }
            val service = MedicationAnalyticsService(medRepo, logRepo, objectMapper)

            assertEquals(3, service.getAdherenceStreak(medicationId))
        }

    @Test
    fun `getAdherenceStreak breaks when there is a gap`(): Unit =
        runBlocking {
            val logs =
                listOf(
                    takenLogAt(today),
                    // gap: today.minusDays(1) missing
                    takenLogAt(today.minusDays(2)),
                    takenLogAt(today.minusDays(3)),
                )
            val logRepo: MedicationLogRepository =
                mock {
                    onBlocking {
                        findByMedicationIdAndTakenAtBetweenOrderByTakenAtDesc(any(), any(), any())
                    } doReturn logs
                }
            val medRepo: MedicationRepository =
                mock { onBlocking { findById(any()) } doReturn dailyMedication() }
            val service = MedicationAnalyticsService(medRepo, logRepo, objectMapper)

            // streak is 1 (only today), broken by the gap
            assertEquals(1, service.getAdherenceStreak(medicationId))
        }

    @Test
    fun `getAggregateHeatmap returns entry per day with correct ratio`(): Unit =
        runBlocking {
            val yesterday = today.minusDays(1)
            val log =
                MedicationLog(
                    id = UUID.randomUUID(),
                    medicationId = medicationId,
                    userId = userId,
                    takenAt = yesterday.atStartOfDay(ZoneOffset.UTC).plusHours(8).toInstant(),
                    taken = true,
                )
            val logRepo: MedicationLogRepository =
                mock {
                    onBlocking { findByUserIdAndTakenAtBetweenOrderByTakenAtDesc(any(), any(), any()) } doReturn
                        listOf(log)
                }
            val medRepo: MedicationRepository =
                mock {
                    onBlocking { findByUserIdOrderByNameAsc(any()) } doReturn listOf(dailyMedication())
                }
            val service = MedicationAnalyticsService(medRepo, logRepo, objectMapper)

            val result = service.getAggregateHeatmap(userId, 1)

            val yesterdayEntry = result.days.find { it.date == yesterday.toString() }
            assertNotNull(yesterdayEntry)
            assertEquals(1, yesterdayEntry!!.takenCount)
            assertEquals(1, yesterdayEntry.scheduledCount)
            assertEquals(1.0, yesterdayEntry.adherenceRatio, 0.001)
        }

    private fun assertNotNull(value: Any?) = assertEquals(true, value != null)
}
