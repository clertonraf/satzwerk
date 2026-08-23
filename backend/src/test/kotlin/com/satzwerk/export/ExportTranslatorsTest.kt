package com.satzwerk.export

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class ExportTranslatorsTest {
    private val objectMapper = ObjectMapper().findAndRegisterModules()
    private val registry = ExportTranslatorRegistry(objectMapper)

    @Test
    fun `version 1 translator imports legacy payload without medication fields`() {
        val payload =
            objectMapper.valueToTree<JsonNode>(
                mapOf(
                    "version" to 1,
                    "exportedAt" to "2026-01-01T00:00:00Z",
                    "profile" to mapOf("email" to "legacy@test.com", "displayName" to "Legacy"),
                    "exercises" to
                        listOf(
                            mapOf(
                                "id" to UUID.randomUUID(),
                                "name" to "Squat",
                                "muscleGroup" to "LEGS",
                                "description" to null,
                                "videoUrl" to null,
                                "equipment" to null,
                                "createdAt" to "2026-01-01T00:00:00Z",
                            ),
                        ),
                    "workoutPlans" to emptyList<Any>(),
                    "workoutSessions" to emptyList<Any>(),
                ),
            )

        val translator = registry.forImport(payload)
        val snapshot = translator.importSnapshot(payload)

        assertEquals(1, translator.version)
        assertEquals("Legacy", snapshot.profile.displayName)
        assertTrue(snapshot.medications.isEmpty())
        assertTrue(snapshot.medicationLogs.isEmpty())
    }

    @Test
    fun `current translator exports version 2 payload with medication fields`() {
        val medicationId = UUID.randomUUID()
        val snapshot =
            ExportSnapshot(
                exportedAt = Instant.parse("2026-01-01T00:00:00Z"),
                profile = ExportProfileDto(email = "exporter@test.com", displayName = "Exporter"),
                exercises = emptyList(),
                workoutPlans = emptyList(),
                workoutSessions = emptyList(),
                medications =
                    listOf(
                        ExportMedicationDto(
                            id = medicationId,
                            name = "Creatine",
                            dosageAmount = BigDecimal("5.0"),
                            dosageUnit = "G",
                            frequency = mapOf("type" to "DAILY", "timesPerDay" to 1),
                            purpose = "Recovery",
                            isActive = true,
                            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
                        ),
                    ),
                medicationLogs =
                    listOf(
                        ExportMedicationLogDto(
                            id = UUID.randomUUID(),
                            medicationId = medicationId,
                            takenAt = Instant.parse("2026-01-02T07:00:00Z"),
                            taken = true,
                            doseAmount = BigDecimal("5.0"),
                            notes = "Post workout",
                        ),
                    ),
            )

        val payload = registry.current().export(snapshot)
        val root = objectMapper.valueToTree<JsonNode>(payload)

        assertEquals(2, root.path("version").asInt())
        assertEquals(1, root.path("medications").size())
        assertEquals(1, root.path("medicationLogs").size())
    }

    @Test
    fun `version 2 translator imports older payload when set log rir is missing`() {
        val payload =
            objectMapper.valueToTree<JsonNode>(
                mapOf(
                    "version" to 2,
                    "exportedAt" to "2026-01-01T00:00:00Z",
                    "profile" to mapOf("email" to "legacy-v2@test.com", "displayName" to "LegacyV2"),
                    "exercises" to emptyList<Any>(),
                    "workoutPlans" to emptyList<Any>(),
                    "workoutSessions" to
                        listOf(
                            mapOf(
                                "id" to UUID.randomUUID(),
                                "workoutGroupId" to UUID.randomUUID(),
                                "startedAt" to "2026-01-01T00:00:00Z",
                                "completedAt" to "2026-01-01T01:00:00Z",
                                "notes" to null,
                                "setLogs" to
                                    listOf(
                                        mapOf(
                                            "id" to UUID.randomUUID(),
                                            "exerciseId" to UUID.randomUUID(),
                                            "setNumber" to 1,
                                            "weight" to BigDecimal("100.0"),
                                            "reps" to 5,
                                            "loggedAt" to "2026-01-01T00:30:00Z",
                                            "isPr" to false,
                                        ),
                                    ),
                            ),
                        ),
                    "medications" to emptyList<Any>(),
                    "medicationLogs" to emptyList<Any>(),
                ),
            )

        val translator = registry.forImport(payload)
        val snapshot = translator.importSnapshot(payload)
        val importedSetLog = snapshot.workoutSessions.single().setLogs.single()

        assertEquals(2, translator.version)
        assertEquals(null, importedSetLog.rir)
    }
}
