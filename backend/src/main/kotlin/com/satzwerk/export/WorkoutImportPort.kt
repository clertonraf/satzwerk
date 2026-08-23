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
 * Deep persistence boundary for workout import writes.
 *
 * Owns the ID-wiring between plan → group → exercise and session → set-log so callers can supply
 * template entities without knowing the generated IDs ahead of time.
 */
@Component
class WorkoutImportPort(
    private val workoutPlanRepository: WorkoutPlanRepository,
    private val workoutGroupRepository: WorkoutGroupRepository,
    private val workoutExerciseRepository: WorkoutExerciseRepository,
    private val workoutSessionRepository: WorkoutSessionRepository,
    private val setLogRepository: SetLogRepository,
) {
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
