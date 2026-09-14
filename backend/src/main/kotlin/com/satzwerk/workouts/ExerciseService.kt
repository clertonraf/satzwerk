package com.satzwerk.workouts

import com.satzwerk.common.NotFoundException
import com.satzwerk.common.Owned
import com.satzwerk.common.TransactionRunner
import com.satzwerk.common.assertOwner
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class ExerciseService(
    private val exerciseRepository: ExerciseRepository,
    private val exerciseCatalogCache: ExerciseCatalogCache,
    private val transactionRunner: TransactionRunner,
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
        transactionRunner.afterCommit {
            exerciseCatalogCache.invalidateUser(userId)
        }

        return ExerciseResponse.from(exercise)
    }

    suspend fun list(
        userId: UUID,
        muscleGroup: String?,
    ): List<ExerciseResponse> {
        val cached = exerciseCatalogCache.lookup(userId, muscleGroup)
        return cached.value
            ?: loadExerciseList(userId, muscleGroup).also { exercises ->
                exerciseCatalogCache.put(userId, muscleGroup, cached.version, exercises)
            }
    }

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
        transactionRunner.afterCommit {
            exerciseCatalogCache.invalidateUser(userId)
        }

        return ExerciseResponse.from(updated)
    }

    suspend fun delete(
        userId: UUID,
        exerciseId: UUID,
    ) {
        val exercise = getRequiredExercise(userId, exerciseId)
        exerciseRepository.deleteById(requireNotNull(exercise.id))
        transactionRunner.afterCommit {
            exerciseCatalogCache.invalidateUser(userId)
        }
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

    private suspend fun loadExerciseList(
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
}
