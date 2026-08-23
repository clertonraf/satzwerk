package com.satzwerk.export

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.satzwerk.common.BadRequestException
import java.time.Instant

private const val CURRENT_EXPORT_VERSION = 2
private const val SUPPORTED_EXPORT_VERSIONS_LABEL = "1, 2"

internal data class ExportSnapshot(
    val exportedAt: Instant = Instant.now(),
    val profile: ExportProfileDto,
    val exercises: List<ExportExerciseDto>,
    val workoutPlans: List<ExportWorkoutPlanDto>,
    val workoutSessions: List<ExportWorkoutSessionDto>,
    val medications: List<ExportMedicationDto> = emptyList(),
    val medicationLogs: List<ExportMedicationLogDto> = emptyList(),
)

internal interface ExportTranslator {
    val version: Int

    fun export(snapshot: ExportSnapshot): Any

    fun importSnapshot(root: JsonNode): ExportSnapshot
}

internal class ExportTranslatorRegistry(
    private val objectMapper: ObjectMapper,
) {
    private val translators =
        listOf(
            ExportV1Translator(objectMapper),
            ExportV2Translator(objectMapper),
        ).associateBy { it.version }

    fun current(): ExportTranslator = requireNotNull(translators[CURRENT_EXPORT_VERSION])

    fun forImport(root: JsonNode): ExportTranslator {
        val versionNode = root.get("version")
        val version =
            versionNode
                ?.takeIf { it.canConvertToInt() }
                ?.asInt()
                ?: throw unsupportedVersion(versionNode?.asText())

        return translators[version] ?: throw unsupportedVersion(version.toString())
    }
}

private class ExportV1Translator(
    private val objectMapper: ObjectMapper,
) : ExportTranslator {
    override val version: Int = 1

    override fun export(snapshot: ExportSnapshot): Any =
        UserDataExportV1Dto(
            exportedAt = snapshot.exportedAt,
            profile = snapshot.profile,
            exercises = snapshot.exercises,
            workoutPlans = snapshot.workoutPlans,
            workoutSessions = snapshot.workoutSessions,
        )

    override fun importSnapshot(root: JsonNode): ExportSnapshot =
        objectMapper.treeToValue(root, UserDataExportV1Dto::class.java).toSnapshot()
}

private class ExportV2Translator(
    private val objectMapper: ObjectMapper,
) : ExportTranslator {
    override val version: Int = 2

    override fun export(snapshot: ExportSnapshot): Any =
        UserDataExportV2Dto(
            exportedAt = snapshot.exportedAt,
            profile = snapshot.profile,
            exercises = snapshot.exercises,
            workoutPlans = snapshot.workoutPlans,
            workoutSessions = snapshot.workoutSessions,
            medications = snapshot.medications,
            medicationLogs = snapshot.medicationLogs,
        )

    override fun importSnapshot(root: JsonNode): ExportSnapshot =
        objectMapper.treeToValue(root, UserDataExportV2Dto::class.java).toSnapshot()
}

private fun UserDataExportV1Dto.toSnapshot() =
    ExportSnapshot(
        exportedAt = exportedAt,
        profile = profile,
        exercises = exercises,
        workoutPlans = workoutPlans,
        workoutSessions = workoutSessions,
    )

private fun UserDataExportV2Dto.toSnapshot() =
    ExportSnapshot(
        exportedAt = exportedAt,
        profile = profile,
        exercises = exercises,
        workoutPlans = workoutPlans,
        workoutSessions = workoutSessions,
        medications = medications,
        medicationLogs = medicationLogs,
    )

private fun unsupportedVersion(version: String?): BadRequestException {
    val rendered = version ?: "missing"
    return BadRequestException(
        "Unsupported export version: $rendered. Supported versions: $SUPPORTED_EXPORT_VERSIONS_LABEL.",
    )
}
