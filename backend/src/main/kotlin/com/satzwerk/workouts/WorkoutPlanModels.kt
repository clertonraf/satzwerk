package com.satzwerk.workouts

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import java.time.Instant
import java.util.UUID

private const val ADVANCED_TECHNIQUE_PATTERN = "SST|REST_PAUSE|GVT|FST_7|GIRONDA"

data class WorkoutPlanResponse(
    val id: UUID,
    val name: String,
    val source: String,
    val isActive: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class WorkoutGroupResponse(
    val id: UUID,
    val title: String,
    val orderIndex: Int,
)

data class WorkoutExerciseResponse(
    val id: UUID,
    val exerciseId: UUID,
    val exerciseName: String,
    val sets: Int,
    val reps: Int,
    val toFailure: Boolean,
    val advancedTechnique: String?,
    val orderIndex: Int,
)

data class WorkoutPlanDetailResponse(
    val id: UUID,
    val name: String,
    val source: String,
    val isActive: Boolean,
    val groups: List<WorkoutGroupDetailResponse>,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class WorkoutGroupDetailResponse(
    val id: UUID,
    val title: String,
    val orderIndex: Int,
    val exercises: List<WorkoutExerciseResponse>,
)

data class CreatePlanRequest(
    @field:NotBlank
    val name: String,
)

data class UpdatePlanRequest(
    @field:NotBlank
    val name: String? = null,
)

data class CreateGroupRequest(
    @field:NotBlank
    val title: String,
    val orderIndex: Int = 0,
)

data class UpdateGroupRequest(
    @field:NotBlank
    val title: String? = null,
    val orderIndex: Int? = null,
)

data class CreateWorkoutExerciseRequest(
    @field:NotNull
    val exerciseId: UUID,
    @field:Min(1)
    val sets: Int,
    @field:Min(1)
    val reps: Int,
    @field:Pattern(regexp = ADVANCED_TECHNIQUE_PATTERN)
    val advancedTechnique: String? = null,
    val orderIndex: Int = 0,
)

data class UpdateWorkoutExerciseRequest(
    @field:Min(1)
    val sets: Int? = null,
    @field:Min(1)
    val reps: Int? = null,
    @field:Pattern(regexp = ADVANCED_TECHNIQUE_PATTERN)
    val advancedTechnique: String? = null,
    val orderIndex: Int? = null,
)

data class ReorderRequest(
    @field:NotNull
    val direction: ReorderDirection,
)

enum class ReorderDirection {
    UP,
    DOWN,
}
