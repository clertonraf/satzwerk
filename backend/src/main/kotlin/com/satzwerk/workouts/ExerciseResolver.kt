package com.satzwerk.workouts

import kotlinx.coroutines.flow.toList
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class ExerciseResolver(private val exerciseRepository: ExerciseRepository) {
    /**
     * Looks up existing exercises by userId and name (case-insensitive) and creates any that are missing.
     *
     * @param nameToMuscleGroup map of original-cased exercise name → muscle group, deduplicated by caller
     * @return map of lowercase exercise name → Exercise
     */
    suspend fun resolve(
        userId: UUID,
        nameToMuscleGroup: Map<String, String>,
    ): Map<String, Exercise> {
        if (nameToMuscleGroup.isEmpty()) return emptyMap()

        val nameLowerToOriginal = nameToMuscleGroup.keys.associateBy { it.lowercase() }

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

        return existingByNameLower + newExercises.associateBy { it.name.lowercase() }
    }
}
