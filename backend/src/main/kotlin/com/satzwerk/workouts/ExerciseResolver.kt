package com.satzwerk.workouts

import kotlinx.coroutines.flow.toList
import org.springframework.stereotype.Component
import java.util.UUID

data class ExerciseResolution(
    val exercisesByNameLower: Map<String, Exercise>,
    val createdCount: Int,
)

@Component
class ExerciseResolver(
    private val exerciseRepository: ExerciseRepository,
) {
    /**
     * Looks up existing exercises by userId and name (case-insensitive) and creates any that are missing.
     *
     * Case-insensitive collisions (e.g. "Bench Press" vs "bench press") are resolved by first-occurrence wins.
     *
     * @param nameToMuscleGroup map of original-cased exercise name → muscle group
     * @return [ExerciseResolution] containing the lowercase-name lookup map plus the number of created Exercises
     */
    suspend fun resolve(
        userId: UUID,
        nameToMuscleGroup: Map<String, String>,
    ): ExerciseResolution {
        if (nameToMuscleGroup.isEmpty()) {
            return ExerciseResolution(exercisesByNameLower = emptyMap(), createdCount = 0)
        }

        // Use putIfAbsent so first occurrence wins on case-insensitive collisions.
        val nameLowerToOriginal =
            buildMap<String, String> {
                nameToMuscleGroup.keys.forEach { name -> putIfAbsent(name.lowercase(), name) }
            }

        val existingByNameLower =
            exerciseRepository.findAllByUserIdAndNamesLowercase(userId, nameLowerToOriginal.keys)
                .associateBy { it.name.lowercase() }

        val toCreate =
            nameLowerToOriginal.filterKeys { it !in existingByNameLower }
                .map { (_, originalName) ->
                    Exercise(
                        userId = userId,
                        name = originalName,
                        muscleGroup = nameToMuscleGroup[originalName].orEmpty(),
                    )
                }

        val newExercises =
            if (toCreate.isEmpty()) {
                emptyList()
            } else {
                exerciseRepository.saveAll(toCreate).toList()
            }

        return ExerciseResolution(
            exercisesByNameLower = existingByNameLower + newExercises.associateBy { it.name.lowercase() },
            createdCount = newExercises.size,
        )
    }
}
