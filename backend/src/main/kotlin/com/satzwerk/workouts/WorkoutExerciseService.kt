package com.satzwerk.workouts

import com.satzwerk.common.ForbiddenException
import com.satzwerk.common.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class WorkoutExerciseService(
    private val workoutGroupService: WorkoutGroupService,
    private val workoutExerciseRepository: WorkoutExerciseRepository,
    private val exerciseRepository: ExerciseRepository,
) {
    suspend fun create(
        userId: UUID,
        planId: UUID,
        groupId: UUID,
        request: CreateWorkoutExerciseRequest,
    ): WorkoutExerciseResponse {
        workoutGroupService.getRequiredGroup(userId, planId, groupId)
        val exercise = exerciseRepository.findById(request.exerciseId) ?: throw NotFoundException("Exercise not found")
        if (exercise.userId != userId) {
            throw ForbiddenException("Exercise does not belong to user")
        }

        return workoutExerciseRepository
            .save(
                WorkoutExercise(
                    workoutGroupId = groupId,
                    exerciseId = request.exerciseId,
                    sets = request.sets,
                    reps = request.reps,
                    advancedTechnique = request.advancedTechnique,
                    orderIndex = request.orderIndex,
                ),
            ).toResponse(exercise.name)
    }

    suspend fun update(
        userId: UUID,
        planId: UUID,
        groupId: UUID,
        exerciseId: UUID,
        request: UpdateWorkoutExerciseRequest,
    ): WorkoutExerciseResponse {
        workoutGroupService.getRequiredGroup(userId, planId, groupId)
        val existing = getRequiredWorkoutExercise(groupId, exerciseId)
        val exercise = exerciseRepository.findById(existing.exerciseId) ?: throw NotFoundException("Exercise not found")
        return workoutExerciseRepository
            .save(
                existing.copy(
                    sets = request.sets ?: existing.sets,
                    reps = request.reps ?: existing.reps,
                    advancedTechnique = request.advancedTechnique ?: existing.advancedTechnique,
                    orderIndex = request.orderIndex ?: existing.orderIndex,
                    updatedAt = Instant.now(),
                ),
            ).toResponse(exercise.name)
    }

    suspend fun delete(
        userId: UUID,
        planId: UUID,
        groupId: UUID,
        exerciseId: UUID,
    ) {
        workoutGroupService.getRequiredGroup(userId, planId, groupId)
        val workoutExercise = getRequiredWorkoutExercise(groupId, exerciseId)
        workoutExerciseRepository.deleteById(requireNotNull(workoutExercise.id))
    }

    @Transactional
    suspend fun reorder(
        userId: UUID,
        planId: UUID,
        groupId: UUID,
        exerciseId: UUID,
        direction: ReorderDirection,
    ): List<WorkoutExerciseResponse> {
        workoutGroupService.getRequiredGroup(userId, planId, groupId)
        val exercises = workoutExerciseRepository.findAllByWorkoutGroupIdOrderByOrderIndex(groupId)
        val targetIndex = exercises.indexOfFirst { it.id == exerciseId }
        if (targetIndex == -1) {
            throw NotFoundException("Workout exercise not found")
        }

        val swapIndex =
            when (direction) {
                ReorderDirection.UP -> targetIndex - 1
                ReorderDirection.DOWN -> targetIndex + 1
            }
        if (swapIndex !in exercises.indices) {
            return workoutExerciseRepository.findAllWithNameByWorkoutGroupId(groupId)
                .map { it.toResponse() }
        }

        val now = Instant.now()
        val target = exercises[targetIndex]
        val partner = exercises[swapIndex]
        workoutExerciseRepository.save(target.copy(orderIndex = partner.orderIndex, updatedAt = now))
        workoutExerciseRepository.save(partner.copy(orderIndex = target.orderIndex, updatedAt = now))

        return workoutExerciseRepository.findAllWithNameByWorkoutGroupId(groupId)
            .map { it.toResponse() }
    }

    private suspend fun getRequiredWorkoutExercise(
        groupId: UUID,
        exerciseId: UUID,
    ): WorkoutExercise =
        workoutExerciseRepository.findByIdAndWorkoutGroupId(exerciseId, groupId)
            ?: throw NotFoundException("Workout exercise not found")
}
