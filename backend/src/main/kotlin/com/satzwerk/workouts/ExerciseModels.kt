package com.satzwerk.workouts

import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.util.UUID

data class CreateExerciseRequest(
    @field:NotBlank
    val name: String,
    @field:NotBlank
    val muscleGroup: String,
    val description: String? = null,
    val videoUrl: String? = null,
    val equipment: String? = null,
)

data class UpdateExerciseRequest(
    val name: String? = null,
    val muscleGroup: String? = null,
    val description: String? = null,
    val videoUrl: String? = null,
    val equipment: String? = null,
)

data class ExerciseResponse(
    val id: UUID,
    val name: String,
    val muscleGroup: String,
    val description: String?,
    val videoUrl: String?,
    val equipment: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        internal fun from(exercise: Exercise): ExerciseResponse =
            ExerciseResponse(
                id = requireNotNull(exercise.id),
                name = exercise.name,
                muscleGroup = exercise.muscleGroup,
                description = exercise.description,
                videoUrl = exercise.videoUrl,
                equipment = exercise.equipment,
                createdAt = exercise.createdAt,
                updatedAt = exercise.updatedAt,
            )
    }
}
