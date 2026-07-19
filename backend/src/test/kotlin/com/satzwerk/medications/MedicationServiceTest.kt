package com.satzwerk.medications

import com.fasterxml.jackson.databind.ObjectMapper
import com.satzwerk.common.ConflictException
import com.satzwerk.common.NotFoundException
import io.r2dbc.postgresql.codec.Json
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class MedicationServiceTest {
    private val objectMapper = ObjectMapper().findAndRegisterModules()
    private val userId = UUID.randomUUID()
    private val medicationId = UUID.randomUUID()

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
    fun `createMedication saves entity and returns response`(): Unit =
        runBlocking {
            val saved = dailyMedication()
            val medRepo: MedicationRepository =
                mock {
                    onBlocking { findByUserIdAndNameIgnoreCase(any(), any()) } doReturn null
                    onBlocking { save(any()) } doReturn saved
                }
            val logRepo: MedicationLogRepository = mock()
            val analyticsService: MedicationAnalyticsService =
                mock {
                    onBlocking { getAdherenceStreak(any()) } doReturn 0
                }
            val service = MedicationService(medRepo, logRepo, analyticsService, objectMapper)

            val request =
                CreateMedicationRequest(
                    name = "Vitamin D",
                    dosageAmount = BigDecimal("1000"),
                    dosageUnit = DosageUnit.IU,
                    frequency = FrequencySpec.Daily(timesPerDay = 1),
                )
            val response = service.createMedication(userId, request)

            assertEquals("Vitamin D", response.name)
            assertEquals(DosageUnit.IU, response.dosageUnit)
            assertEquals(0, response.currentStreak)
        }

    @Test
    fun `createMedication throws ConflictException on duplicate name`(): Unit =
        runBlocking {
            val medRepo: MedicationRepository =
                mock {
                    onBlocking { findByUserIdAndNameIgnoreCase(any(), any()) } doReturn dailyMedication()
                }
            val logRepo: MedicationLogRepository = mock()
            val analyticsService: MedicationAnalyticsService = mock()
            val service = MedicationService(medRepo, logRepo, analyticsService, objectMapper)

            val request =
                CreateMedicationRequest(
                    name = "Vitamin D",
                    dosageAmount = BigDecimal("1000"),
                    dosageUnit = DosageUnit.IU,
                    frequency = FrequencySpec.Daily(timesPerDay = 1),
                )
            assertThrows<ConflictException> { service.createMedication(userId, request) }
        }

    @Test
    fun `deactivateMedication sets isActive to false`(): Unit =
        runBlocking {
            val existing = dailyMedication()
            val medRepo: MedicationRepository =
                mock {
                    onBlocking { findByUserIdAndId(any(), any()) } doReturn existing
                    onBlocking { save(any()) } doReturn existing.copy(isActive = false)
                }
            val service =
                MedicationService(medRepo, mock(), mock(), objectMapper)

            service.deactivateMedication(userId, medicationId)

            val captor = argumentCaptor<Medication>()
            verify(medRepo).save(captor.capture())
            assertFalse(captor.firstValue.isActive)
        }

    @Test
    fun `deactivateMedication throws NotFoundException when medication not owned`(): Unit =
        runBlocking {
            val medRepo: MedicationRepository =
                mock {
                    onBlocking { findByUserIdAndId(any(), any()) } doReturn null
                }
            val service = MedicationService(medRepo, mock(), mock(), objectMapper)

            assertThrows<NotFoundException> { service.deactivateMedication(userId, medicationId) }
        }

    @Test
    fun `logDose saves a MedicationLog and returns response`(): Unit =
        runBlocking {
            val medRepo: MedicationRepository =
                mock {
                    onBlocking { findByUserIdAndId(any(), any()) } doReturn dailyMedication()
                }
            val log =
                MedicationLog(
                    id = UUID.randomUUID(),
                    medicationId = medicationId,
                    userId = userId,
                    takenAt = Instant.now(),
                    taken = true,
                )
            val logRepo: MedicationLogRepository =
                mock {
                    onBlocking { save(any()) } doReturn log
                }
            val service = MedicationService(medRepo, logRepo, mock(), objectMapper)

            val request = LogDoseRequest(takenAt = Instant.now(), taken = true)
            val response = service.logDose(userId, medicationId, request)

            assertTrue(response.taken)
            assertNotNull(response.id)
        }
}

class IsDueTodayTest {
    @Test
    fun `daily frequency is always due`() {
        val spec = FrequencySpec.Daily(timesPerDay = 2)
        assertTrue(isDueToday(spec, LocalDate.of(2024, 6, 15)))
    }

    @Test
    fun `weekly frequency is due on matching weekday`() {
        // June 15, 2024 is Saturday = ISO 6
        val spec = FrequencySpec.Weekly(timesPerWeek = 1, weekdays = listOf(6))
        assertTrue(isDueToday(spec, LocalDate.of(2024, 6, 15)))
    }

    @Test
    fun `weekly frequency is not due on non-matching weekday`() {
        // June 15, 2024 is Saturday = ISO 6; spec says Monday = 1
        val spec = FrequencySpec.Weekly(timesPerWeek = 1, weekdays = listOf(1))
        assertFalse(isDueToday(spec, LocalDate.of(2024, 6, 15)))
    }

    @Test
    fun `weekly frequency with empty weekdays is always due`() {
        val spec = FrequencySpec.Weekly(timesPerWeek = 3, weekdays = emptyList())
        assertTrue(isDueToday(spec, LocalDate.of(2024, 6, 15)))
    }

    @Test
    fun `monthly frequency is due on matching day of month`() {
        val spec = FrequencySpec.Monthly(timesPerMonth = 2, daysOfMonth = listOf(1, 15))
        assertTrue(isDueToday(spec, LocalDate.of(2024, 6, 15)))
    }

    @Test
    fun `monthly frequency is not due on non-matching day of month`() {
        val spec = FrequencySpec.Monthly(timesPerMonth = 2, daysOfMonth = listOf(1, 15))
        assertFalse(isDueToday(spec, LocalDate.of(2024, 6, 10)))
    }

    @Test
    fun `scheduledCountForToday returns timesPerDay for DAILY`() {
        val spec = FrequencySpec.Daily(timesPerDay = 3)
        assertEquals(3, scheduledCountForToday(spec))
    }

    @Test
    fun `scheduledCountForToday returns 1 for WEEKLY`() {
        val spec = FrequencySpec.Weekly(timesPerWeek = 3)
        assertEquals(1, scheduledCountForToday(spec))
    }

    @Test
    fun `scheduledCountForToday returns 1 for MONTHLY`() {
        val spec = FrequencySpec.Monthly(timesPerMonth = 2)
        assertEquals(1, scheduledCountForToday(spec))
    }
}
