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
import java.time.ZoneOffset
import java.util.UUID

class MedicationServiceTest {
    private val objectMapper = ObjectMapper().findAndRegisterModules()
    private val frequencySpecModule = FrequencySpecModule(objectMapper)
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
            val service = MedicationService(medRepo, logRepo, analyticsService, frequencySpecModule)

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
            val service = MedicationService(medRepo, logRepo, analyticsService, frequencySpecModule)

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
                MedicationService(medRepo, mock(), mock(), frequencySpecModule)

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
            val service = MedicationService(medRepo, mock(), mock(), frequencySpecModule)

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
            val service = MedicationService(medRepo, logRepo, mock(), frequencySpecModule)

            val request = LogDoseRequest(takenAt = Instant.now(), taken = true)
            val response = service.logDose(userId, medicationId, request)

            assertTrue(response.taken)
            assertNotNull(response.id)
        }

    @Test
    fun `getJournalView returns empty days when no logs exist`(): Unit =
        runBlocking {
            val from = Instant.parse("2026-08-01T00:00:00Z")
            val to = Instant.parse("2026-08-31T23:59:59.999999999Z")
            val logRepo: MedicationLogRepository =
                mock {
                    onBlocking {
                        findByUserIdAndTakenAtBetweenOrderByTakenAtDesc(userId, from, to)
                    } doReturn emptyList()
                }
            val service = MedicationService(mock(), logRepo, mock(), frequencySpecModule)

            val result = service.getJournalView(userId, from, to, ZoneOffset.UTC)

            assertTrue(result.days.isEmpty())
        }

    @Test
    fun `getJournalView groups entries by requested timezone day`(): Unit =
        runBlocking {
            val from = Instant.parse("2026-08-01T00:00:00Z")
            val to = Instant.parse("2026-08-31T23:59:59.999999999Z")
            val logId = UUID.randomUUID()
            val takenAt = Instant.parse("2026-08-15T23:30:00Z")
            val log =
                MedicationLog(
                    id = logId,
                    medicationId = medicationId,
                    userId = userId,
                    takenAt = takenAt,
                    taken = true,
                    doseAmount = BigDecimal("500"),
                    notes = "With breakfast",
                )
            val med = dailyMedication()
            val logRepo: MedicationLogRepository =
                mock {
                    onBlocking {
                        findByUserIdAndTakenAtBetweenOrderByTakenAtDesc(userId, from, to)
                    } doReturn listOf(log)
                }
            val medRepo: MedicationRepository =
                mock {
                    onBlocking { findByUserIdOrderByNameAsc(userId) } doReturn listOf(med)
                }
            val service = MedicationService(medRepo, logRepo, mock(), frequencySpecModule)

            val result = service.getJournalView(userId, from, to, ZoneOffset.ofHours(2))

            assertEquals(1, result.days.size)
            assertEquals("2026-08-16", result.days[0].date)
            assertEquals(1, result.days[0].entries.size)
            with(result.days[0].entries[0]) {
                assertEquals(logId, id)
                assertEquals(medicationId, this.medicationId)
                assertEquals("Vitamin D", medicationName)
                assertEquals(takenAt, this.takenAt)
                assertTrue(taken)
                assertEquals(BigDecimal("500"), doseAmount)
                assertEquals(DosageUnit.IU, dosageUnit)
                assertEquals("With breakfast", notes)
            }
        }

    @Test
    fun `getTodayView returns scheduled doses alongside manual logging options`(): Unit =
        runBlocking {
            val today = LocalDate.now(ZoneOffset.UTC)
            val startOfDay = today.atStartOfDay(ZoneOffset.UTC).toInstant()
            val endOfDay = today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
            val scheduledMedication = dailyMedication()
            val extraMedication =
                dailyMedication().copy(
                    id = UUID.randomUUID(),
                    name = "Magnesium",
                    dosageAmount = BigDecimal("400.0000"),
                )
            val todaysLog =
                MedicationLog(
                    id = UUID.randomUUID(),
                    medicationId = medicationId,
                    userId = userId,
                    takenAt = startOfDay.plusSeconds(3600),
                    taken = true,
                )
            val medRepo: MedicationRepository =
                mock {
                    onBlocking { findByUserIdOrderByNameAsc(userId) } doReturn
                        listOf(extraMedication, scheduledMedication)
                }
            val logRepo: MedicationLogRepository =
                mock {
                    onBlocking {
                        findByMedicationIdAndTakenAtBetweenOrderByTakenAtDesc(medicationId, startOfDay, endOfDay)
                    } doReturn listOf(todaysLog)
                    onBlocking {
                        findByMedicationIdAndTakenAtBetweenOrderByTakenAtDesc(
                            requireNotNull(extraMedication.id),
                            startOfDay,
                            endOfDay,
                        )
                    } doReturn emptyList()
                }
            val analyticsService: MedicationAnalyticsService =
                mock {
                    onBlocking { getAdherenceStreaksBatch(any()) } doReturn
                        mapOf(
                            medicationId to 3,
                            requireNotNull(extraMedication.id) to 1,
                        )
                }
            val service = MedicationService(medRepo, logRepo, analyticsService, frequencySpecModule)

            val result = service.getTodayView(userId)

            assertEquals(listOf("Magnesium", "Vitamin D"), result.availableMedications.map { it.name })
            assertEquals(2, result.scheduledDoses.size)
            assertEquals("Vitamin D", result.scheduledDoses[1].medication.name)
            assertEquals(1, result.scheduledDoses[1].logs.size)
        }
}
