package com.satzwerk.export

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class ExportProfileDto(
    val email: String,
    val displayName: String,
)

data class ExportExerciseDto(
    val id: UUID,
    val name: String,
    val muscleGroup: String,
    val description: String?,
    val videoUrl: String?,
    val equipment: String?,
    val createdAt: Instant,
)

data class ExportWorkoutExerciseDto(
    val id: UUID,
    val exerciseId: UUID,
    val sets: Int,
    val reps: Int,
    val toFailure: Boolean,
    val advancedTechnique: String?,
    val orderIndex: Int,
)

data class ExportWorkoutGroupDto(
    val id: UUID,
    val title: String,
    val orderIndex: Int,
    val exercises: List<ExportWorkoutExerciseDto>,
)

data class ExportWorkoutPlanDto(
    val id: UUID,
    val name: String,
    val source: String,
    val isActive: Boolean,
    val activatedAt: Instant?,
    val createdAt: Instant,
    val groups: List<ExportWorkoutGroupDto>,
)

data class ExportSetLogDto(
    val id: UUID,
    val exerciseId: UUID,
    val setNumber: Int,
    val weight: BigDecimal,
    val reps: Int,
    val loggedAt: Instant,
    val isPr: Boolean,
)

data class ExportWorkoutSessionDto(
    val id: UUID,
    val workoutGroupId: UUID,
    val startedAt: Instant,
    val completedAt: Instant?,
    val notes: String?,
    val setLogs: List<ExportSetLogDto>,
)

data class UserDataExportDto(
    val version: Int = 1,
    val exportedAt: Instant = Instant.now(),
    val profile: ExportProfileDto,
    val exercises: List<ExportExerciseDto>,
    val workoutPlans: List<ExportWorkoutPlanDto>,
    val workoutSessions: List<ExportWorkoutSessionDto>,
)

data class ImportSummaryDto(
    val importedExercises: Int,
    val importedWorkoutPlans: Int,
    val importedWorkoutSessions: Int,
    val importedSetLogs: Int,
    val reusedExercises: Int,
)
