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

/** Identifies a workout group to be imported together with its exercises. */
data class ImportGroupSpec(
    val exportedId: UUID,
    val group: WorkoutGroup,
    val exercises: List<WorkoutExercise>,
)

/**
 * Deep persistence boundary for import/export operations.
 *
 * Provides query methods for the export path and deep write methods for the import path.
 * The import methods own the ID-wiring between plan → group → exercise and session → set-log,
 * so callers construct template entities without knowing the newly-assigned IDs.
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

    suspend fun findSetLogsBySessionIds(sessionIds: List<UUID>): Map<UUID, List<SetLog>> =
        setLogRepository.findAllByWorkoutSessionIdIn(sessionIds).groupBy { it.workoutSessionId }

    /**
     * Saves [plan] along with each group and its exercises, wiring the generated IDs at each level.
     *
     * @return a map from each [ImportGroupSpec.exportedId] to the newly-assigned group ID,
     *         allowing callers to remap session references from the original export.
     */
    suspend fun importPlanWithGroups(
        plan: WorkoutPlan,
        groupSpecs: List<ImportGroupSpec>,
    ): Map<UUID, UUID> {
        val savedPlan = workoutPlanRepository.save(plan)
        val newPlanId = requireNotNull(savedPlan.id)
        val groupIdMap = mutableMapOf<UUID, UUID>()
        for (spec in groupSpecs) {
            val savedGroup = workoutGroupRepository.save(spec.group.copy(workoutPlanId = newPlanId))
            val newGroupId = requireNotNull(savedGroup.id)
            groupIdMap[spec.exportedId] = newGroupId
            for (exercise in spec.exercises) {
                workoutExerciseRepository.save(exercise.copy(workoutGroupId = newGroupId))
            }
        }
        return groupIdMap
    }

    /**
     * Saves [session] and all [setLogs], wiring [SetLog.workoutSessionId] to the newly-assigned session ID.
     *
     * @return the number of set logs saved.
     */
    suspend fun importSessionWithSetLogs(
        session: WorkoutSession,
        setLogs: List<SetLog>,
    ): Int {
        val savedSession = workoutSessionRepository.save(session)
        val newSessionId = requireNotNull(savedSession.id)
        for (setLog in setLogs) {
            setLogRepository.save(setLog.copy(workoutSessionId = newSessionId))
        }
        return setLogs.size
    }
}
