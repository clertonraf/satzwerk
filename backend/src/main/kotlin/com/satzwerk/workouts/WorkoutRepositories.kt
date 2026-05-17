package com.satzwerk.workouts

import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import java.util.UUID

interface WorkoutPlanRepository : CoroutineCrudRepository<WorkoutPlan, UUID> {
    suspend fun findAllByUserId(userId: UUID): List<WorkoutPlan>

    suspend fun findByIdAndUserId(
        id: UUID,
        userId: UUID,
    ): WorkoutPlan?

    suspend fun findAllByUserIdAndIsActive(
        userId: UUID,
        isActive: Boolean,
    ): List<WorkoutPlan>
}

interface WorkoutGroupRepository : CoroutineCrudRepository<WorkoutGroup, UUID> {
    suspend fun findAllByWorkoutPlanIdOrderByOrderIndex(planId: UUID): List<WorkoutGroup>

    suspend fun findByIdAndWorkoutPlanId(
        id: UUID,
        planId: UUID,
    ): WorkoutGroup?
}

interface WorkoutExerciseRepository : CoroutineCrudRepository<WorkoutExercise, UUID> {
    suspend fun findAllByWorkoutGroupIdOrderByOrderIndex(groupId: UUID): List<WorkoutExercise>

    suspend fun findByIdAndWorkoutGroupId(
        id: UUID,
        groupId: UUID,
    ): WorkoutExercise?
}
