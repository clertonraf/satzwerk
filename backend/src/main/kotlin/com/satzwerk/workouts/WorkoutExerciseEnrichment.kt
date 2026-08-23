package com.satzwerk.workouts

import com.satzwerk.common.NotFoundException
import kotlinx.coroutines.flow.toList
import java.util.UUID

internal suspend fun enrichWorkoutExercises(
    workoutExercises: List<WorkoutExercise>,
    exerciseRepository: ExerciseRepository,
): List<WorkoutExerciseResponse> {
    val exerciseNamesById =
        loadExerciseNames(
            workoutExercises.map(WorkoutExercise::exerciseId).toSet(),
            exerciseRepository,
        )
    return toWorkoutExerciseResponses(workoutExercises, exerciseNamesById)
}

internal fun toWorkoutExerciseResponses(
    workoutExercises: List<WorkoutExercise>,
    exerciseNamesById: Map<UUID, String>,
): List<WorkoutExerciseResponse> {
    return workoutExercises.map { workoutExercise ->
        WorkoutExerciseResponse.from(
            workoutExercise,
            exerciseNamesById.requireExerciseName(workoutExercise.exerciseId),
        )
    }
}

internal suspend fun loadExerciseNames(
    exerciseIds: Set<UUID>,
    exerciseRepository: ExerciseRepository,
): Map<UUID, String> {
    if (exerciseIds.isEmpty()) {
        return emptyMap()
    }

    return exerciseRepository.findAllById(exerciseIds)
        .toList()
        .associate { exercise ->
            requireNotNull(exercise.id) to exercise.name
        }
}

private fun Map<UUID, String>.requireExerciseName(exerciseId: UUID): String =
    this[exerciseId] ?: throw NotFoundException("Exercise not found")
