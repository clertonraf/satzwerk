package com.satzwerk.workouts

import com.satzwerk.common.NotFoundException
import com.satzwerk.common.requireOwnership
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class ExerciseService(
    private val exerciseRepository: ExerciseRepository,
) {
    suspend fun create(
        userId: UUID,
        request: CreateExerciseRequest,
    ): ExerciseResponse {
        val exercise =
            exerciseRepository.save(
                Exercise(
                    userId = userId,
                    name = request.name,
                    muscleGroup = request.muscleGroup,
                    description = request.description,
                    videoUrl = request.videoUrl,
                    equipment = request.equipment,
                ),
            )

        return exercise.toResponse()
    }

    suspend fun list(
        userId: UUID,
        muscleGroup: String?,
    ): List<ExerciseResponse> =
        (
            if (muscleGroup.isNullOrBlank()) {
                exerciseRepository.findAllByUserId(userId)
            } else {
                exerciseRepository.findAllByUserIdAndMuscleGroup(userId, muscleGroup)
            }
        ).sortedBy { it.name }
            .map(Exercise::toResponse)

    suspend fun getOwned(
        userId: UUID,
        exerciseId: UUID,
    ): ExerciseResponse = getRequiredExercise(userId, exerciseId).toResponse()

    suspend fun update(
        userId: UUID,
        exerciseId: UUID,
        request: UpdateExerciseRequest,
    ): ExerciseResponse {
        val existing = getRequiredExercise(userId, exerciseId)
        val updated =
            exerciseRepository.save(
                existing.copy(
                    name = request.name ?: existing.name,
                    muscleGroup = request.muscleGroup ?: existing.muscleGroup,
                    description = request.description ?: existing.description,
                    videoUrl = request.videoUrl ?: existing.videoUrl,
                    equipment = request.equipment ?: existing.equipment,
                    updatedAt = Instant.now(),
                ),
            )

        return updated.toResponse()
    }

    suspend fun delete(
        userId: UUID,
        exerciseId: UUID,
    ) {
        val exercise = getRequiredExercise(userId, exerciseId)
        exerciseRepository.deleteById(requireNotNull(exercise.id))
    }

    private suspend fun getRequiredExercise(
        userId: UUID,
        exerciseId: UUID,
    ): Exercise {
        val exercise =
            exerciseRepository.findById(exerciseId)
                ?: throw NotFoundException("Exercise not found")
        requireOwnership(exercise.userId, userId, "Exercise")

        return exercise
    }
}
