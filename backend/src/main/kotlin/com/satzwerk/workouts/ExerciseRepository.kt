package com.satzwerk.workouts

import org.springframework.data.r2dbc.repository.Query
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

    @Query("SELECT * FROM exercises WHERE user_id = :userId AND LOWER(name) IN (:names)")
    suspend fun findAllByUserIdAndNamesLowercase(
        userId: UUID,
        names: Collection<String>,
    ): List<Exercise>
}
