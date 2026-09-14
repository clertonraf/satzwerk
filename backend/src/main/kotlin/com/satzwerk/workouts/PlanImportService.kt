package com.satzwerk.workouts

import com.satzwerk.common.TransactionRunner
import kotlinx.coroutines.flow.toList
import org.springframework.http.codec.multipart.FilePart
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class PlanImportService(
    private val planParser: PlanParser,
    private val planImportDeps: PlanImportDeps,
    private val transactionRunner: TransactionRunner,
) {
    suspend fun import(
        userId: UUID,
        filePart: FilePart,
    ): WorkoutPlanResponse {
        val importResult =
            transactionRunner.required {
                val parsed = planParser.parse(filePart)
                val planName = planImportDeps.planImportParsingAdapters.normalizeFilename(filePart.filename())

                val plan =
                    planImportDeps.workoutPlanRepository.save(
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
                val resolution = planImportDeps.exerciseResolver.resolve(userId, nameToMuscleGroup)
                createGroupsAndExercises(planId, parsed, resolution.exercisesByNameLower)
                PlanImportResult(WorkoutPlanResponse.from(plan), resolution.createdCount)
            }
        if (importResult.createdExerciseCount > 0) {
            planImportDeps.exerciseCatalogCache.invalidateUser(userId)
        }
        return importResult.response
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
        // Sort by orderIndex to guarantee stable pairing with parsed.workouts regardless of saveAll emission order.
        val savedGroups =
            planImportDeps.workoutGroupRepository.saveAll(groupEntities).toList().sortedBy { it.orderIndex }

        val allExercises =
            savedGroups.flatMapIndexed { idx, group ->
                val parsedWorkout = parsed.workouts[idx]
                val groupId = requireNotNull(group.id)
                parsedWorkout.exercises.mapIndexed { exerciseIndex, parsedExercise ->
                    val exercise = requireNotNull(exerciseByNameLower[parsedExercise.exercise.lowercase()])
                    val parsedReps = planImportDeps.planImportParsingAdapters.parseReps(parsedExercise.reps)
                    WorkoutExercise(
                        workoutGroupId = groupId,
                        exerciseId = requireNotNull(exercise.id),
                        sets = parsedExercise.sets,
                        reps = parsedReps.reps,
                        toFailure = parsedReps.toFailure,
                        advancedTechnique =
                            planImportDeps.planImportParsingAdapters.parseTechnique(parsedExercise.advancedTechnique),
                        orderIndex = exerciseIndex,
                    )
                }
            }
        planImportDeps.workoutExerciseRepository.saveAll(allExercises).toList()
    }
}

private data class PlanImportResult(
    val response: WorkoutPlanResponse,
    val createdExerciseCount: Int,
)
