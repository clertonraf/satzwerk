package com.satzwerk.export

import com.satzwerk.sessions.SetLog
import com.satzwerk.sessions.SetLogRepository
import com.satzwerk.sessions.WorkoutSession
import com.satzwerk.sessions.WorkoutSessionRepository
import com.satzwerk.workouts.WorkoutExercise
import com.satzwerk.workouts.WorkoutExerciseRepository
import com.satzwerk.workouts.WorkoutGroup
import com.satzwerk.workouts.WorkoutGroupRepository
import com.satzwerk.workouts.WorkoutPlan
import com.satzwerk.workouts.WorkoutPlanRepository
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Groups the five workout/session repositories behind a single injectable boundary,
 * keeping ExportService constructor parameter count within the detekt threshold.
 */
@Component
class WorkoutDataPort(
    private val workoutPlanRepository: WorkoutPlanRepository,
    private val workoutGroupRepository: WorkoutGroupRepository,
    private val workoutExerciseRepository: WorkoutExerciseRepository,
    private val workoutSessionRepository: WorkoutSessionRepository,
    private val setLogRepository: SetLogRepository,
) {
    suspend fun findOpenSession(userId: UUID): WorkoutSession? =
        workoutSessionRepository.findByUserIdAndCompletedAtIsNull(userId)

    suspend fun findAllPlans(userId: UUID): List<WorkoutPlan> = workoutPlanRepository.findAllByUserId(userId)

    suspend fun findGroupsForPlan(planId: UUID): List<WorkoutGroup> =
        workoutGroupRepository.findAllByWorkoutPlanIdOrderByOrderIndex(planId)

    suspend fun findExercisesForGroup(groupId: UUID): List<WorkoutExercise> =
        workoutExerciseRepository.findAllByWorkoutGroupIdOrderByOrderIndex(groupId)

    suspend fun findAllSessions(userId: UUID): List<WorkoutSession> = workoutSessionRepository.findAllByUserId(userId)

    suspend fun savePlan(plan: WorkoutPlan): WorkoutPlan = workoutPlanRepository.save(plan)

    suspend fun saveGroup(group: WorkoutGroup): WorkoutGroup = workoutGroupRepository.save(group)

    suspend fun saveWorkoutExercise(we: WorkoutExercise): WorkoutExercise = workoutExerciseRepository.save(we)

    suspend fun saveSession(session: WorkoutSession): WorkoutSession = workoutSessionRepository.save(session)

    suspend fun saveSetLog(setLog: SetLog): SetLog = setLogRepository.save(setLog)
}
