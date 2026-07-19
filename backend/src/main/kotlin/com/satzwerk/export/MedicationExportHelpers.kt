package com.satzwerk.export

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.satzwerk.medications.Medication
import com.satzwerk.medications.MedicationLog
import com.satzwerk.medications.MedicationLogRepository
import com.satzwerk.medications.MedicationRepository
import io.r2dbc.postgresql.codec.Json
import java.time.Instant
import java.util.UUID

private const val ONE_DAY_SECONDS = 86400L

internal data class MedicationImportResult(
    val importedCount: Int,
    val reusedCount: Int,
    val importedLogCount: Int,
)

internal data class MedicationImportDeps(
    val medicationRepository: MedicationRepository,
    val medicationLogRepository: MedicationLogRepository,
    val objectMapper: ObjectMapper,
)

internal suspend fun exportMedicationsFor(
    userId: UUID,
    medicationRepository: MedicationRepository,
    objectMapper: ObjectMapper,
): List<ExportMedicationDto> =
    medicationRepository.findByUserIdOrderByNameAsc(userId).map { med ->
        val freqMap: Map<String, Any> =
            objectMapper.readValue(med.frequency.asString(), object : TypeReference<Map<String, Any>>() {})
        ExportMedicationDto(
            id = requireNotNull(med.id),
            name = med.name,
            dosageAmount = med.dosageAmount,
            dosageUnit = med.dosageUnit,
            frequency = freqMap,
            purpose = med.purpose,
            isActive = med.isActive,
            createdAt = med.createdAt,
        )
    }

internal suspend fun exportMedicationLogsFor(
    userId: UUID,
    medicationRepository: MedicationRepository,
    medicationLogRepository: MedicationLogRepository,
): List<ExportMedicationLogDto> {
    val allMeds = medicationRepository.findByUserIdOrderByNameAsc(userId)
    val ids = allMeds.mapNotNull { it.id }
    if (ids.isEmpty()) return emptyList()
    return medicationLogRepository.findByMedicationIdInAndTakenAtBetweenOrderByTakenAtDesc(
        ids,
        Instant.EPOCH,
        Instant.now().plusSeconds(ONE_DAY_SECONDS),
    ).map { log ->
        ExportMedicationLogDto(
            id = requireNotNull(log.id),
            medicationId = log.medicationId,
            takenAt = log.takenAt,
            taken = log.taken,
            doseAmount = log.doseAmount,
            notes = log.notes,
        )
    }
}

internal suspend fun importMedicationsAndLogs(
    userId: UUID,
    medications: List<ExportMedicationDto>,
    medicationLogs: List<ExportMedicationLogDto>,
    deps: MedicationImportDeps,
): MedicationImportResult {
    val medicationIdMap = mutableMapOf<UUID, UUID>()
    var importedCount = 0
    var reusedCount = 0
    for (exportedMed in medications) {
        val existing = deps.medicationRepository.findByUserIdAndNameIgnoreCase(userId, exportedMed.name)
        if (existing != null) {
            medicationIdMap[exportedMed.id] = requireNotNull(existing.id)
            reusedCount++
        } else {
            val frequencyJson = deps.objectMapper.writeValueAsString(exportedMed.frequency)
            val saved =
                deps.medicationRepository.save(
                    Medication(
                        userId = userId,
                        name = exportedMed.name,
                        dosageAmount = exportedMed.dosageAmount,
                        dosageUnit = exportedMed.dosageUnit,
                        frequency = Json.of(frequencyJson),
                        purpose = exportedMed.purpose,
                        isActive = exportedMed.isActive,
                        createdAt = exportedMed.createdAt,
                    ),
                )
            medicationIdMap[exportedMed.id] = requireNotNull(saved.id)
            importedCount++
        }
    }
    var importedLogCount = 0
    for (exportedLog in medicationLogs) {
        val mappedMedId = medicationIdMap[exportedLog.medicationId] ?: continue
        deps.medicationLogRepository.save(
            MedicationLog(
                medicationId = mappedMedId,
                userId = userId,
                takenAt = exportedLog.takenAt,
                taken = exportedLog.taken,
                doseAmount = exportedLog.doseAmount,
                notes = exportedLog.notes,
            ),
        )
        importedLogCount++
    }
    return MedicationImportResult(
        importedCount = importedCount,
        reusedCount = reusedCount,
        importedLogCount = importedLogCount,
    )
}
