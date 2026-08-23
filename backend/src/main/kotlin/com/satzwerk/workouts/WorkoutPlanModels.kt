package com.satzwerk.workouts

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.Instant
import java.util.UUID

data class WorkoutPlanResponse(
    val id: UUID,
    val name: String,
    val source: String,
    val isActive: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        internal fun from(plan: WorkoutPlan): WorkoutPlanResponse =
            WorkoutPlanResponse(
                id = requireNotNull(plan.id),
                name = plan.name,
                source = plan.source,
                isActive = plan.isActive,
                createdAt = plan.createdAt,
                updatedAt = plan.updatedAt,
            )
    }
}

data class WorkoutGroupResponse(
    val id: UUID,
    val title: String,
    val orderIndex: Int,
) {
    companion object {
        internal fun from(group: WorkoutGroup): WorkoutGroupResponse =
            WorkoutGroupResponse(
                id = requireNotNull(group.id),
                title = group.title,
                orderIndex = group.orderIndex,
            )
    }
}

data class WorkoutExerciseResponse(
    val id: UUID,
    val exerciseId: UUID,
    val exerciseName: String,
    val sets: Int,
    val reps: Int,
    val toFailure: Boolean,
    val advancedTechnique: String?,
    val orderIndex: Int,
) {
    companion object {
        internal fun from(
            workoutExercise: WorkoutExercise,
            exerciseName: String,
        ): WorkoutExerciseResponse =
            WorkoutExerciseResponse(
                id = requireNotNull(workoutExercise.id),
                exerciseId = workoutExercise.exerciseId,
                exerciseName = exerciseName,
                sets = workoutExercise.sets,
                reps = workoutExercise.reps,
                toFailure = workoutExercise.toFailure,
                advancedTechnique = workoutExercise.advancedTechnique,
                orderIndex = workoutExercise.orderIndex,
            )
    }
}

data class AdvancedTechniqueMetadataResponse(
    val value: String,
    val label: String,
    val description: String,
    val restSeconds: Int?,
) {
    companion object {
        internal fun from(advancedTechnique: AdvancedTechnique): AdvancedTechniqueMetadataResponse =
            AdvancedTechniqueMetadataResponse(
                value = advancedTechnique.name,
                label = advancedTechnique.label,
                description = advancedTechnique.description,
                restSeconds = advancedTechnique.restSeconds,
            )
    }
}

data class WorkoutPlanDetailResponse(
    val id: UUID,
    val name: String,
    val source: String,
    val isActive: Boolean,
    val groups: List<WorkoutGroupDetailResponse>,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        internal fun from(
            plan: WorkoutPlan,
            groups: List<WorkoutGroup>,
            exercisesByGroup: Map<UUID, List<WorkoutExerciseResponse>>,
        ): WorkoutPlanDetailResponse =
            WorkoutPlanDetailResponse(
                id = requireNotNull(plan.id),
                name = plan.name,
                source = plan.source,
                isActive = plan.isActive,
                groups =
                    groups.map { group ->
                        val groupId = requireNotNull(group.id)
                        WorkoutGroupDetailResponse(
                            id = groupId,
                            title = group.title,
                            orderIndex = group.orderIndex,
                            exercises = exercisesByGroup[groupId].orEmpty(),
                        )
                    },
                createdAt = plan.createdAt,
                updatedAt = plan.updatedAt,
            )
    }
}

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
    @field:ValidAdvancedTechnique
    val advancedTechnique: String? = null,
    val orderIndex: Int = 0,
)

data class UpdateWorkoutExerciseRequest(
    @field:Min(1)
    val sets: Int? = null,
    @field:Min(1)
    val reps: Int? = null,
    @field:ValidAdvancedTechnique
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
