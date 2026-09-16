package com.satzwerk.export

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.satzwerk.analytics.AnalyticsReadCache
import com.satzwerk.common.TransactionRunner
import com.satzwerk.medications.MedicationLogRepository
import com.satzwerk.medications.MedicationRepository
import com.satzwerk.workouts.ExerciseCatalogCache
import com.satzwerk.workouts.ExerciseRepository
import com.satzwerk.workouts.WorkoutGroupReadCache
import com.satzwerk.workouts.WorkoutPlanReadCache
import com.satzwerk.workouts.WorkoutReadCaches
import com.satzwerk.workouts.WorkoutReadPort
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import java.time.Instant
import java.util.UUID

class ExportServiceTest {
    private val exerciseRepository: ExerciseRepository = mock()
    private val exerciseCatalogCache: ExerciseCatalogCache = mock()
    private val workoutPlanReadCache: WorkoutPlanReadCache = mock()
    private val workoutGroupReadCache: WorkoutGroupReadCache = mock()
    private val analyticsReadCache: AnalyticsReadCache = mock()
    private val workoutReadPort: WorkoutReadPort =
        mock {
            onBlocking { findOpenSession(userId) } doReturn null
        }
    private val workoutImportPort: WorkoutImportPort =
        mock {
            onBlocking { importPlanWithGroups(org.mockito.kotlin.any(), org.mockito.kotlin.any()) } doReturn
                ImportedPlanResult(
                    planId = importedPlanId,
                    groupIdMap = emptyMap(),
                )
        }
    private val inlineTransactionRunner =
        object : TransactionRunner {
            override suspend fun <T> required(block: suspend () -> T): T = block()

            override suspend fun afterCommit(block: suspend () -> Unit) {
                block()
            }
        }
    private val service =
        ExportService(
            userRepository = mock(),
            exerciseRepository = exerciseRepository,
            workoutDeps =
                ExportWorkoutDeps(
                    workoutReadPort = workoutReadPort,
                    workoutImportPort = workoutImportPort,
                    workoutReadCaches =
                        WorkoutReadCaches(
                            exerciseCatalogCache = exerciseCatalogCache,
                            workoutPlanReadCache = workoutPlanReadCache,
                            workoutGroupReadCache = workoutGroupReadCache,
                        ),
                    analyticsReadCache = analyticsReadCache,
                ),
            exportSupportDeps =
                ExportSupportDeps(
                    medicationRepository = mock<MedicationRepository>(),
                    medicationLogRepository = mock<MedicationLogRepository>(),
                    objectMapper = jacksonObjectMapper().findAndRegisterModules(),
                    transactionRunner = inlineTransactionRunner,
                ),
        )

    @Test
    fun `import invalidates WorkoutPlan and WorkoutGroup caches when it imports plans`(): Unit =
        runBlocking {
            service.importForUser(userId, exportJsonWithOnePlan())

            verify(workoutPlanReadCache).invalidateList(userId)
            verify(workoutPlanReadCache).invalidateDetail(userId, importedPlanId)
            verify(workoutGroupReadCache).invalidatePlan(userId, importedPlanId)
            verify(exerciseCatalogCache, never()).invalidateUser(userId)
            verify(analyticsReadCache, never()).invalidateUser(userId)
        }

    private fun exportJsonWithOnePlan(): JsonNode =
        jacksonObjectMapper().findAndRegisterModules().valueToTree(
            UserDataExportV2Dto(
                version = 2,
                exportedAt = Instant.parse("2026-01-01T00:00:00Z"),
                profile = ExportProfileDto(email = "import@test.com", displayName = "Import User"),
                exercises = emptyList(),
                workoutPlans =
                    listOf(
                        ExportWorkoutPlanDto(
                            id = UUID.randomUUID(),
                            name = "Imported Plan",
                            source = "MANUAL",
                            isActive = false,
                            activatedAt = null,
                            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
                            groups = emptyList(),
                        ),
                    ),
                workoutSessions = emptyList(),
                medications = emptyList(),
                medicationLogs = emptyList(),
            ),
        )

    private companion object {
        val userId: UUID = UUID.randomUUID()
        val importedPlanId: UUID = UUID.randomUUID()
    }
}
