package com.satzwerk.workouts

import com.fasterxml.jackson.databind.JsonNode
import org.springframework.http.codec.multipart.FilePart
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class PlanImportService(
    private val kraftLogParserClient: KraftLogParserClient,
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
        val parsed = kraftLogParserClient.parse(filePart)
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

        parsed.workouts.forEachIndexed { groupIndex, parsedWorkout ->
            val groupTitle = parsedWorkout.bodyParts
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
            val primaryMuscleGroup = parsedWorkout.bodyParts.firstOrNull()

            parsedWorkout.exercises.forEachIndexed { exerciseIndex, parsedExercise ->
                val exercise = findOrCreateExercise(userId, parsedExercise.exercise, primaryMuscleGroup)
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

        return plan.toResponse()
    }

    private suspend fun findOrCreateExercise(
        userId: UUID,
        name: String,
        muscleGroup: String?,
    ): Exercise =
        exerciseRepository.findByUserIdAndNameIgnoreCase(userId, name)
            ?: exerciseRepository.save(
                Exercise(
                    userId = userId,
                    name = name,
                    muscleGroup = muscleGroup.orEmpty(),
                ),
            )

    private fun mapReps(repsNode: JsonNode): Pair<Int, Boolean> =
        if (repsNode.isTextual && repsNode.asText().uppercase() == "F") {
            0 to true
        } else {
            repsNode.asInt() to false
        }

    internal fun mapTechnique(raw: String?): String? {
        if (raw == null) return null

        val lower = raw.lowercase()
        return when {
            lower.contains("rest") -> AdvancedTechnique.REST_PAUSE.name
            lower.contains("strip") -> AdvancedTechnique.SST.name
            lower.contains("gvt") -> AdvancedTechnique.GVT.name
            lower.contains("fst") -> AdvancedTechnique.FST_7.name
            lower.contains("gironda") -> AdvancedTechnique.GIRONDA.name
            else -> null
        }
    }

    internal fun planNameFromFilename(filename: String): String =
        filename.substringBeforeLast(".")
            .replace("_", " ")
            .replace("-", " ")
            .trim()
}
