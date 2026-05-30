package com.satzwerk.workouts

import com.fasterxml.jackson.databind.JsonNode
import kotlinx.coroutines.flow.toList
import org.springframework.http.codec.multipart.FilePart
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class PlanImportService(
    private val planParser: PlanParser,
    private val workoutPlanRepository: WorkoutPlanRepository,
    private val workoutGroupRepository: WorkoutGroupRepository,
    private val workoutExerciseRepository: WorkoutExerciseRepository,
    private val exerciseRepository: ExerciseRepository,
) {
    @Transactional
    suspend fun import(
        userId: UUID,
        filePart: FilePart,
    ): WorkoutPlanResponse {
        val parsed = planParser.parse(filePart)
        val planName = planNameFromFilename(filePart.filename())

        val plan =
            workoutPlanRepository.save(
                WorkoutPlan(
                    userId = userId,
                    name = planName,
                    source = WorkoutSource.IMPORTED.name,
                    isActive = false,
                ),
            )
        val planId = requireNotNull(plan.id)

        val exerciseByNameLower = resolveExercises(userId, parsed)
        createGroupsAndExercises(planId, parsed, exerciseByNameLower)

        return plan.toResponse()
    }

    private suspend fun createGroupsAndExercises(
        planId: UUID,
        parsed: SatzwerkParserResponse,
        exerciseByNameLower: Map<String, Exercise>,
    ) {
        parsed.workouts.forEachIndexed { groupIndex, parsedWorkout ->
            val groupTitle =
                parsedWorkout.bodyParts
                    .filter { it.isNotBlank() }
                    .joinToString(", ")
                    .ifBlank { parsedWorkout.name }

            val group =
                workoutGroupRepository.save(
                    WorkoutGroup(
                        workoutPlanId = planId,
                        title = groupTitle,
                        orderIndex = groupIndex,
                    ),
                )
            val groupId = requireNotNull(group.id)

            parsedWorkout.exercises.forEachIndexed { exerciseIndex, parsedExercise ->
                val exercise = requireNotNull(exerciseByNameLower[parsedExercise.exercise.lowercase()])
                val (reps, toFailure) = mapReps(parsedExercise.reps)

                workoutExerciseRepository.save(
                    WorkoutExercise(
                        workoutGroupId = groupId,
                        exerciseId = requireNotNull(exercise.id),
                        sets = parsedExercise.sets,
                        reps = reps,
                        toFailure = toFailure,
                        advancedTechnique = mapTechnique(parsedExercise.advancedTechnique),
                        orderIndex = exerciseIndex,
                    ),
                )
            }
        }
    }

    private suspend fun resolveExercises(
        userId: UUID,
        parsed: SatzwerkParserResponse,
    ): Map<String, Exercise> {
        val nameLowerToOriginal =
            buildMap<String, String> {
                parsed.workouts.forEach { workout ->
                    workout.exercises.forEach { ex -> putIfAbsent(ex.exercise.lowercase(), ex.exercise) }
                }
            }
        val nameLowerToMuscleGroup =
            buildMap<String, String> {
                parsed.workouts.forEach { workout ->
                    val muscleGroup = workout.bodyParts.firstOrNull().orEmpty()
                    workout.exercises.forEach { ex -> putIfAbsent(ex.exercise.lowercase(), muscleGroup) }
                }
            }

        val existingByNameLower =
            if (nameLowerToOriginal.isEmpty()) {
                emptyMap()
            } else {
                exerciseRepository.findAllByUserIdAndNamesLowercase(userId, nameLowerToOriginal.keys)
                    .associateBy { it.name.lowercase() }
            }

        val newExercises =
            exerciseRepository.saveAll(
                nameLowerToOriginal.filterKeys { it !in existingByNameLower }
                    .map { (nameLower, originalName) ->
                        Exercise(
                            userId = userId,
                            name = originalName,
                            muscleGroup = nameLowerToMuscleGroup[nameLower].orEmpty(),
                        )
                    },
            ).toList()

        return existingByNameLower + newExercises.associateBy { it.name.lowercase() }
    }

    private fun mapReps(repsNode: JsonNode): Pair<Int, Boolean> =
        if (repsNode.isTextual && repsNode.asText().uppercase() == "F") {
            0 to true
        } else {
            repsNode.asInt() to false
        }

    internal fun mapTechnique(raw: String?): String? =
        raw?.let {
            AdvancedTechnique.fromParserString(it)?.name
        }

    internal fun planNameFromFilename(filename: String): String =
        filename.substringBeforeLast(".")
            .replace("_", " ")
            .replace("-", " ")
            .trim()
}
