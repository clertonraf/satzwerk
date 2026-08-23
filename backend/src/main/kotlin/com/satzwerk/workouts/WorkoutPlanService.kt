package com.satzwerk.workouts

import com.satzwerk.common.ConflictException
import com.satzwerk.common.NotFoundException
import com.satzwerk.common.Owned
import com.satzwerk.common.assertOwner
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class WorkoutPlanService(
    private val workoutPlanRepository: WorkoutPlanRepository,
    private val workoutGroupRepository: WorkoutGroupRepository,
    private val workoutExerciseRepository: WorkoutExerciseRepository,
    private val exerciseRepository: ExerciseRepository,
) {
    suspend fun create(
        userId: UUID,
        request: CreatePlanRequest,
    ): WorkoutPlanResponse =
        workoutPlanRepository
            .save(
                WorkoutPlan(
                    userId = userId,
                    name = request.name,
                ),
            ).let(WorkoutPlanResponse::from)

    suspend fun list(userId: UUID): List<WorkoutPlanResponse> =
        workoutPlanRepository.findAllByUserId(userId)
            .sortedBy(WorkoutPlan::createdAt)
            .map(WorkoutPlanResponse::from)

    suspend fun getDetail(
        userId: UUID,
        planId: UUID,
    ): WorkoutPlanDetailResponse {
        val plan = getRequiredPlan(userId, planId)
        val groups = workoutGroupRepository.findAllByWorkoutPlanIdOrderByOrderIndex(planId)
        val groupIds = groups.mapNotNull(WorkoutGroup::id)
        val workoutExercises =
            if (groupIds.isEmpty()) {
                emptyList()
            } else {
                workoutExerciseRepository.findAllByWorkoutGroupIdInOrderByWorkoutGroupIdAscOrderIndexAsc(
                    groupIds,
                )
            }
        val exerciseNamesById =
            loadExerciseNames(
                workoutExercises.map(WorkoutExercise::exerciseId).toSet(),
                exerciseRepository,
            )

        val exercisesByGroup =
            workoutExercises
                .groupBy { it.workoutGroupId }
                .mapValues { (_, exercises) -> toWorkoutExerciseResponses(exercises, exerciseNamesById) }

        return WorkoutPlanDetailResponse.from(plan, groups, exercisesByGroup)
    }

    suspend fun getActiveDetail(userId: UUID): WorkoutPlanDetailResponse {
        val activePlan = requireActivePlan(userId)
        return getDetail(userId, requireNotNull(activePlan.id))
    }

    /** Returns the active WorkoutPlan for [userId], or throws [NotFoundException] if none is active. */
    suspend fun requireActivePlan(userId: UUID): WorkoutPlan =
        workoutPlanRepository.findAllByUserIdAndIsActive(userId, true).firstOrNull()
            ?: throw NotFoundException("No active workout plan found")

    suspend fun update(
        userId: UUID,
        planId: UUID,
        request: UpdatePlanRequest,
    ): WorkoutPlanResponse {
        val existing = getRequiredPlan(userId, planId)
        val updated =
            workoutPlanRepository.save(
                existing.copy(
                    name = request.name ?: existing.name,
                    updatedAt = Instant.now(),
                ),
            )

        return WorkoutPlanResponse.from(updated)
    }

    suspend fun delete(
        userId: UUID,
        planId: UUID,
    ) {
        val plan = getRequiredPlan(userId, planId)
        workoutPlanRepository.deleteById(requireNotNull(plan.id))
    }

    @Transactional
    suspend fun activate(
        userId: UUID,
        planId: UUID,
    ) {
        val plan = getRequiredPlan(userId, planId)
        val now = Instant.now()
        workoutPlanRepository.findAllByUserIdAndIsActive(userId, true)
            .filter { it.id != plan.id }
            .forEach { activePlan ->
                workoutPlanRepository.save(activePlan.copy(isActive = false, activatedAt = null, updatedAt = now))
            }
        try {
            workoutPlanRepository.save(plan.copy(isActive = true, activatedAt = now, updatedAt = now))
        } catch (_: DataIntegrityViolationException) {
            throw ConflictException("Another WorkoutPlan was activated concurrently")
        }
    }

    suspend fun getRequiredPlan(
        userId: UUID,
        planId: UUID,
    ): WorkoutPlan = fetchPlanAsOwned(planId, workoutPlanRepository).assertOwner(userId, "Workout plan").value

    suspend fun getRequiredGroup(
        userId: UUID,
        planId: UUID,
        groupId: UUID,
    ): WorkoutGroup {
        getRequiredPlan(userId, planId)
        return workoutGroupRepository.findByIdAndWorkoutPlanId(groupId, planId)
            ?: throw NotFoundException("Workout group not found")
    }
}

private suspend fun fetchPlanAsOwned(
    planId: UUID,
    workoutPlanRepository: WorkoutPlanRepository,
): Owned<WorkoutPlan> {
    val plan = workoutPlanRepository.findById(planId) ?: throw NotFoundException("Workout plan not found")
    return Owned(plan, plan.userId)
}
