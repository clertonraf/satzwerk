package com.satzwerk.workouts

import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import java.time.Instant
import java.util.UUID

data class WorkoutExerciseWithName(
    val id: UUID,
    val workoutGroupId: UUID,
    val exerciseId: UUID,
    val exerciseName: String,
    val sets: Int,
    val reps: Int,
    val toFailure: Boolean,
    val advancedTechnique: String?,
    val orderIndex: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
)

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

    @Query(
        """
        SELECT we.id, we.workout_group_id, we.exercise_id, e.name AS exercise_name,
               we.sets, we.reps, we.to_failure, we.advanced_technique, we.order_index,
               we.created_at, we.updated_at
        FROM workout_exercises we
        JOIN exercises e ON e.id = we.exercise_id
        WHERE we.workout_group_id = :groupId
        ORDER BY we.order_index
        """,
    )
    suspend fun findAllWithNameByWorkoutGroupId(groupId: UUID): List<WorkoutExerciseWithName>

    @Query(
        """
        SELECT we.id, we.workout_group_id, we.exercise_id, e.name AS exercise_name,
               we.sets, we.reps, we.to_failure, we.advanced_technique, we.order_index,
               we.created_at, we.updated_at
        FROM workout_exercises we
        JOIN exercises e ON e.id = we.exercise_id
        JOIN workout_groups wg ON wg.id = we.workout_group_id
        WHERE wg.workout_plan_id = :planId
        ORDER BY we.order_index
        """,
    )
    suspend fun findAllWithNameByPlanId(planId: UUID): List<WorkoutExerciseWithName>
}
