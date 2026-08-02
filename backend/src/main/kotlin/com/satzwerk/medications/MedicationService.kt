package com.satzwerk.medications

import com.fasterxml.jackson.databind.ObjectMapper
import com.satzwerk.common.ConflictException
import com.satzwerk.common.NotFoundException
import io.r2dbc.postgresql.codec.Json
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

@Service
class MedicationService(
    private val medicationRepository: MedicationRepository,
    private val medicationLogRepository: MedicationLogRepository,
    private val medicationAnalyticsService: MedicationAnalyticsService,
    private val objectMapper: ObjectMapper,
) {
    suspend fun createMedication(
        userId: UUID,
        request: CreateMedicationRequest,
    ): MedicationResponse {
        val duplicate = medicationRepository.findByUserIdAndNameIgnoreCase(userId, request.name)
        if (duplicate != null) throw ConflictException("Medication '${request.name}' already exists")
        val entity =
            Medication(
                userId = userId,
                name = request.name,
                dosageAmount = request.dosageAmount,
                dosageUnit = request.dosageUnit.name,
                frequency = serializeFrequency(request.frequency, objectMapper),
                purpose = request.purpose,
            )
        val saved = medicationRepository.save(entity)
        return toMedicationResponse(saved, objectMapper, streak = 0)
    }

    suspend fun getMedications(userId: UUID): List<MedicationResponse> {
        val meds = medicationRepository.findByUserIdOrderByNameAsc(userId)
        val streaks = medicationAnalyticsService.getAdherenceStreaksBatch(meds)
        return meds.map { med -> toMedicationResponse(med, objectMapper, streaks[med.id] ?: 0) }
    }

    suspend fun getMedication(
        userId: UUID,
        id: UUID,
    ): MedicationResponse {
        val med = requireOwnedMedication(medicationRepository, userId, id)
        val streak = medicationAnalyticsService.getAdherenceStreak(id)
        return toMedicationResponse(med, objectMapper, streak)
    }

    suspend fun updateMedication(
        userId: UUID,
        id: UUID,
        request: UpdateMedicationRequest,
    ): MedicationResponse {
        val existing = requireOwnedMedication(medicationRepository, userId, id)
        if (!existing.name.equals(request.name, ignoreCase = true)) {
            val duplicate = medicationRepository.findByUserIdAndNameIgnoreCase(userId, request.name)
            if (duplicate != null) throw ConflictException("Medication '${request.name}' already exists")
        }
        val updated =
            existing.copy(
                name = request.name,
                dosageAmount = request.dosageAmount,
                dosageUnit = request.dosageUnit.name,
                frequency = serializeFrequency(request.frequency, objectMapper),
                purpose = request.purpose,
                updatedAt = Instant.now(),
            )
        val saved = medicationRepository.save(updated)
        val streak = medicationAnalyticsService.getAdherenceStreak(id)
        return toMedicationResponse(saved, objectMapper, streak)
    }

    suspend fun deactivateMedication(
        userId: UUID,
        id: UUID,
    ) {
        val existing = requireOwnedMedication(medicationRepository, userId, id)
        medicationRepository.save(existing.copy(isActive = false, updatedAt = Instant.now()))
    }

    suspend fun logDose(
        userId: UUID,
        medicationId: UUID,
        request: LogDoseRequest,
    ): MedicationLogResponse {
        requireOwnedMedication(medicationRepository, userId, medicationId)
        val log =
            MedicationLog(
                medicationId = medicationId,
                userId = userId,
                takenAt = request.takenAt,
                taken = request.taken,
                doseAmount = request.doseAmount,
                notes = request.notes,
            )
        return medicationLogRepository.save(log).toResponse()
    }

    suspend fun getLogsForMedication(
        userId: UUID,
        medicationId: UUID,
        from: Instant,
        to: Instant,
    ): List<MedicationLogResponse> {
        requireOwnedMedication(medicationRepository, userId, medicationId)
        return medicationLogRepository
            .findByMedicationIdAndTakenAtBetweenOrderByTakenAtDesc(medicationId, from, to)
            .map { it.toResponse() }
    }

    suspend fun getJournalEntries(
        userId: UUID,
        from: Instant,
        to: Instant,
    ): List<MedicationJournalEntryDto> {
        val logs = medicationLogRepository.findByUserIdAndTakenAtBetweenOrderByTakenAtDesc(userId, from, to)
        if (logs.isEmpty()) return emptyList()
        val medicationsById =
            medicationRepository.findByUserIdOrderByNameAsc(userId).associateBy { requireNotNull(it.id) }
        return logs.mapNotNull { log ->
            val med = medicationsById[log.medicationId] ?: return@mapNotNull null
            MedicationJournalEntryDto(
                id = requireNotNull(log.id),
                medicationId = log.medicationId,
                medicationName = med.name,
                takenAt = log.takenAt,
                taken = log.taken,
                doseAmount = log.doseAmount,
                dosageAmount = med.dosageAmount,
                dosageUnit = med.dosageUnit,
                notes = log.notes,
            )
        }
    }

    suspend fun getTodayScheduledDoses(userId: UUID): List<ScheduledDoseSummaryDto> {
        val today = LocalDate.now(ZoneOffset.UTC)
        val startOfDay = today.atStartOfDay(ZoneOffset.UTC).toInstant()
        val endOfDay = today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()

        return medicationRepository.findByUserIdOrderByNameAsc(userId)
            .filter { it.isActive }
            .filter { isDueToday(deserializeFrequency(it.frequency, objectMapper), today) }
            .map { med ->
                val logs =
                    medicationLogRepository.findByMedicationIdAndTakenAtBetweenOrderByTakenAtDesc(
                        requireNotNull(med.id),
                        startOfDay,
                        endOfDay,
                    )
                val streak = medicationAnalyticsService.getAdherenceStreak(requireNotNull(med.id))
                ScheduledDoseSummaryDto(
                    medication = toMedicationResponse(med, objectMapper, streak),
                    scheduledCount = scheduledCountForToday(deserializeFrequency(med.frequency, objectMapper)),
                    logs = logs.map { it.toResponse() },
                )
            }
    }
}

suspend fun requireOwnedMedication(
    repo: MedicationRepository,
    userId: UUID,
    id: UUID,
): Medication = repo.findByUserIdAndId(userId, id) ?: throw NotFoundException("Medication $id not found")

fun serializeFrequency(
    spec: FrequencySpec,
    mapper: ObjectMapper,
): Json = Json.of(mapper.writeValueAsString(spec))

fun deserializeFrequency(
    json: Json,
    mapper: ObjectMapper,
): FrequencySpec = mapper.readValue(json.asString(), FrequencySpec::class.java)

fun toMedicationResponse(
    med: Medication,
    mapper: ObjectMapper,
    streak: Int,
): MedicationResponse =
    MedicationResponse(
        id = requireNotNull(med.id),
        name = med.name,
        dosageAmount = med.dosageAmount,
        dosageUnit = DosageUnit.valueOf(med.dosageUnit),
        frequency = deserializeFrequency(med.frequency, mapper),
        purpose = med.purpose,
        isActive = med.isActive,
        createdAt = med.createdAt,
        currentStreak = streak,
    )

fun isDueToday(
    spec: FrequencySpec,
    today: LocalDate,
): Boolean =
    when (spec) {
        is FrequencySpec.Daily -> true
        is FrequencySpec.Weekly -> {
            val isoDay = today.dayOfWeek.value
            spec.weekdays.isEmpty() || isoDay in spec.weekdays
        }
        is FrequencySpec.Monthly -> {
            val day = today.dayOfMonth
            val daysInMonth = today.lengthOfMonth()
            // Treat days beyond the month length as due on the last day of the month
            spec.daysOfMonth.isEmpty() ||
                day in spec.daysOfMonth ||
                (day == daysInMonth && spec.daysOfMonth.any { it > daysInMonth })
        }
    }

fun scheduledCountForToday(spec: FrequencySpec): Int =
    when (spec) {
        is FrequencySpec.Daily -> spec.timesPerDay
        is FrequencySpec.Weekly -> 1
        is FrequencySpec.Monthly -> 1
    }
