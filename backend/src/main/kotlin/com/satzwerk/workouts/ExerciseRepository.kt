package com.satzwerk.workouts

import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import java.util.UUID

interface ExerciseRepository : CoroutineCrudRepository<Exercise, UUID> {
    suspend fun findAllByUserId(userId: UUID): List<Exercise>

    suspend fun findAllByUserIdAndMuscleGroup(
        userId: UUID,
        muscleGroup: String,
    ): List<Exercise>

    suspend fun findByUserIdAndNameIgnoreCase(
        userId: UUID,
        name: String,
    ): Exercise?

    suspend fun findByIdAndUserId(
        id: UUID,
        userId: UUID,
    ): Exercise?
}
