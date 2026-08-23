package com.satzwerk.medications

import com.satzwerk.common.ConflictException
import com.satzwerk.common.NotFoundException
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
    private val frequencySpecModule: FrequencySpecModule,
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
                frequency = frequencySpecModule.serialize(request.frequency),
                purpose = request.purpose,
            )
        val saved = medicationRepository.save(entity)
        return toMedicationResponse(saved, frequencySpecModule, streak = 0)
    }

    suspend fun getMedications(userId: UUID): List<MedicationResponse> {
        val meds = medicationRepository.findByUserIdOrderByNameAsc(userId)
        val streaks = medicationAnalyticsService.getAdherenceStreaksBatch(meds)
        return meds.map { med -> toMedicationResponse(med, frequencySpecModule, streaks[med.id] ?: 0) }
    }

    suspend fun getMedication(
        userId: UUID,
        id: UUID,
    ): MedicationResponse {
        val med = requireOwnedMedication(medicationRepository, userId, id)
        val streak = medicationAnalyticsService.getAdherenceStreak(id)
        return toMedicationResponse(med, frequencySpecModule, streak)
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
                frequency = frequencySpecModule.serialize(request.frequency),
                purpose = request.purpose,
                updatedAt = Instant.now(),
            )
        val saved = medicationRepository.save(updated)
        val streak = medicationAnalyticsService.getAdherenceStreak(id)
        return toMedicationResponse(saved, frequencySpecModule, streak)
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

    suspend fun getJournalView(
        userId: UUID,
        from: Instant,
        to: Instant,
        zoneOffset: ZoneOffset,
    ): MedicationJournalDto {
        val logs = medicationLogRepository.findByUserIdAndTakenAtBetweenOrderByTakenAtDesc(userId, from, to)
        if (logs.isEmpty()) return MedicationJournalDto(days = emptyList())
        val medicationsById =
            medicationRepository.findByUserIdOrderByNameAsc(userId).associateBy { requireNotNull(it.id) }
        val groupedEntries =
            logs
                .map { log -> toMedicationJournalEntry(log, medicationsById) }
                .groupBy { entry -> entry.takenAt.atOffset(zoneOffset).toLocalDate().toString() }
        return MedicationJournalDto(
            days = groupedEntries.map { (date, entries) -> MedicationJournalDayDto(date = date, entries = entries) },
        )
    }

    suspend fun getTodayView(userId: UUID): MedicationTodayDto {
        val today = LocalDate.now(ZoneOffset.UTC)
        val startOfDay = today.atStartOfDay(ZoneOffset.UTC).toInstant()
        val endOfDay = today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
        val medications = medicationRepository.findByUserIdOrderByNameAsc(userId).filter { it.isActive }
        val streaks = medicationAnalyticsService.getAdherenceStreaksBatch(medications)
        val scheduledDoses =
            medications
                .map { med -> med to frequencySpecModule.deserialize(med.frequency) }
                .filter { (_, frequencySpec) -> frequencySpec.isDueOn(today) }
                .map { (med, frequencySpec) ->
                    val medicationId = requireNotNull(med.id)
                    val logs =
                        medicationLogRepository.findByMedicationIdAndTakenAtBetweenOrderByTakenAtDesc(
                            medicationId,
                            startOfDay,
                            endOfDay,
                        )
                    ScheduledDoseSummaryDto(
                        medication = toMedicationResponse(med, frequencySpecModule, streaks[medicationId] ?: 0),
                        scheduledCount = frequencySpec.scheduledCountOn(today),
                        logs = logs.map { it.toResponse() },
                    )
                }
        return MedicationTodayDto(
            scheduledDoses = scheduledDoses,
            availableMedications =
                medications.map { med ->
                    MedicationOptionDto(
                        id = requireNotNull(med.id),
                        name = med.name,
                    )
                },
        )
    }
}

suspend fun requireOwnedMedication(
    repo: MedicationRepository,
    userId: UUID,
    id: UUID,
): Medication = repo.findByUserIdAndId(userId, id) ?: throw NotFoundException("Medication $id not found")

fun toMedicationResponse(
    med: Medication,
    frequencySpecModule: FrequencySpecModule,
    streak: Int,
): MedicationResponse =
    MedicationResponse(
        id = requireNotNull(med.id),
        name = med.name,
        dosageAmount = med.dosageAmount,
        dosageUnit = DosageUnit.valueOf(med.dosageUnit),
        frequency = frequencySpecModule.deserialize(med.frequency),
        purpose = med.purpose,
        isActive = med.isActive,
        createdAt = med.createdAt,
        currentStreak = streak,
    )

private fun toMedicationJournalEntry(
    log: MedicationLog,
    medicationsById: Map<UUID, Medication>,
): MedicationJournalEntryDto {
    val med =
        medicationsById[log.medicationId]
            ?: error(
                "Medication ${log.medicationId} not found for log ${log.id} — FK invariant violation",
            )
    return MedicationJournalEntryDto(
        id = requireNotNull(log.id),
        medicationId = log.medicationId,
        medicationName = med.name,
        takenAt = log.takenAt,
        taken = log.taken,
        doseAmount = log.doseAmount,
        dosageAmount = med.dosageAmount,
        dosageUnit = DosageUnit.valueOf(med.dosageUnit),
        notes = log.notes,
    )
}
