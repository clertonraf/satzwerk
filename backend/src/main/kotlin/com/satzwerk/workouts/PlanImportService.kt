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
    private val exerciseResolver: ExerciseResolver,
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

        val nameToMuscleGroup =
            buildMap<String, String> {
                parsed.workouts.forEach { workout ->
                    val muscleGroup = workout.bodyParts.firstOrNull().orEmpty()
                    workout.exercises.forEach { ex -> putIfAbsent(ex.exercise, muscleGroup) }
                }
            }
        val exerciseByNameLower = exerciseResolver.resolve(userId, nameToMuscleGroup)
        createGroupsAndExercises(planId, parsed, exerciseByNameLower)

        return plan.toResponse()
    }

    private suspend fun createGroupsAndExercises(
        planId: UUID,
        parsed: SatzwerkParserResponse,
        exerciseByNameLower: Map<String, Exercise>,
    ) {
        val groupEntities =
            parsed.workouts.mapIndexed { groupIndex, parsedWorkout ->
                val groupTitle =
                    parsedWorkout.bodyParts
                        .filter { it.isNotBlank() }
                        .joinToString(", ")
                        .ifBlank { parsedWorkout.name }
                WorkoutGroup(
                    workoutPlanId = planId,
                    title = groupTitle,
                    orderIndex = groupIndex,
                )
            }
        val savedGroups = workoutGroupRepository.saveAll(groupEntities).toList()

        val allExercises =
            savedGroups.flatMapIndexed { idx, group ->
                val parsedWorkout = parsed.workouts[idx]
                val groupId = requireNotNull(group.id)
                parsedWorkout.exercises.mapIndexed { exerciseIndex, parsedExercise ->
                    val exercise = requireNotNull(exerciseByNameLower[parsedExercise.exercise.lowercase()])
                    val (reps, toFailure) = mapReps(parsedExercise.reps)
                    WorkoutExercise(
                        workoutGroupId = groupId,
                        exerciseId = requireNotNull(exercise.id),
                        sets = parsedExercise.sets,
                        reps = reps,
                        toFailure = toFailure,
                        advancedTechnique = mapTechnique(parsedExercise.advancedTechnique),
                        orderIndex = exerciseIndex,
                    )
                }
            }
        workoutExerciseRepository.saveAll(allExercises).toList()
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
