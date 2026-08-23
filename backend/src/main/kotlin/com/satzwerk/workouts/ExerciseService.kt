package com.satzwerk.workouts

import com.satzwerk.common.NotFoundException
import com.satzwerk.common.Owned
import com.satzwerk.common.assertOwner
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

        return ExerciseResponse.from(exercise)
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
            .map(ExerciseResponse::from)

    suspend fun getOwned(
        userId: UUID,
        exerciseId: UUID,
    ): ExerciseResponse = ExerciseResponse.from(getRequiredExercise(userId, exerciseId))

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

        return ExerciseResponse.from(updated)
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
        Owned(exercise, exercise.userId).assertOwner(userId, "Exercise")

        return exercise
    }
}
