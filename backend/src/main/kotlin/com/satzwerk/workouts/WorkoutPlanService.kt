package com.satzwerk.workouts

import com.satzwerk.common.ForbiddenException
import com.satzwerk.common.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class WorkoutPlanService(
    private val workoutPlanRepository: WorkoutPlanRepository,
    private val workoutGroupRepository: WorkoutGroupRepository,
    private val workoutExerciseRepository: WorkoutExerciseRepository,
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
            ).toResponse()

    suspend fun list(userId: UUID): List<WorkoutPlanResponse> =
        workoutPlanRepository.findAllByUserId(userId)
            .sortedBy(WorkoutPlan::createdAt)
            .map(WorkoutPlan::toResponse)

    suspend fun getDetail(
        userId: UUID,
        planId: UUID,
    ): WorkoutPlanDetailResponse {
        val plan = getOwnedPlan(userId, planId)
        val groups = workoutGroupRepository.findAllByWorkoutPlanIdOrderByOrderIndex(planId)

        val exercisesByGroup =
            groups.associate { group ->
                val groupId = requireNotNull(group.id)
                val exercises =
                    workoutExerciseRepository.findAllWithNameByWorkoutGroupId(groupId)
                        .map { it.toResponse() }
                groupId to exercises
            }

        return plan.toDetailResponse(groups, exercisesByGroup)
    }

    suspend fun update(
        userId: UUID,
        planId: UUID,
        request: UpdatePlanRequest,
    ): WorkoutPlanResponse {
        val existing = getOwnedPlan(userId, planId)
        val updated =
            workoutPlanRepository.save(
                existing.copy(
                    name = request.name ?: existing.name,
                    updatedAt = Instant.now(),
                ),
            )

        return updated.toResponse()
    }

    suspend fun delete(
        userId: UUID,
        planId: UUID,
    ) {
        val plan = getOwnedPlan(userId, planId)
        workoutPlanRepository.deleteById(requireNotNull(plan.id))
    }

    @Transactional
    suspend fun activate(
        userId: UUID,
        planId: UUID,
    ) {
        val plan = getOwnedPlan(userId, planId)
        val now = Instant.now()
        workoutPlanRepository.findAllByUserIdAndIsActive(userId, true)
            .filter { it.id != plan.id }
            .forEach { activePlan ->
                workoutPlanRepository.save(activePlan.copy(isActive = false, updatedAt = now))
            }
        workoutPlanRepository.save(plan.copy(isActive = true, updatedAt = now))
    }

    suspend fun getOwnedPlan(
        userId: UUID,
        planId: UUID,
    ): WorkoutPlan {
        val plan = workoutPlanRepository.findById(planId) ?: throw NotFoundException("Workout plan not found")
        if (plan.userId != userId) {
            throw ForbiddenException("Workout plan does not belong to user")
        }

        return plan
    }
}

fun WorkoutPlan.toResponse(): WorkoutPlanResponse =
    WorkoutPlanResponse(
        id = requireNotNull(id),
        name = name,
        source = source,
        isActive = isActive,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun WorkoutPlan.toDetailResponse(
    groups: List<WorkoutGroup>,
    exercisesByGroup: Map<UUID, List<WorkoutExerciseResponse>>,
): WorkoutPlanDetailResponse =
    WorkoutPlanDetailResponse(
        id = requireNotNull(id),
        name = name,
        source = source,
        isActive = isActive,
        groups =
            groups.map { group ->
                WorkoutGroupDetailResponse(
                    id = requireNotNull(group.id),
                    title = group.title,
                    orderIndex = group.orderIndex,
                    exercises = exercisesByGroup[requireNotNull(group.id)].orEmpty(),
                )
            },
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun WorkoutExercise.toResponse(exerciseName: String): WorkoutExerciseResponse =
    WorkoutExerciseResponse(
        id = requireNotNull(id),
        exerciseId = exerciseId,
        exerciseName = exerciseName,
        sets = sets,
        reps = reps,
        toFailure = toFailure,
        advancedTechnique = advancedTechnique,
        orderIndex = orderIndex,
    )

fun WorkoutExerciseWithName.toResponse(): WorkoutExerciseResponse =
    WorkoutExerciseResponse(
        id = id,
        exerciseId = exerciseId,
        exerciseName = exerciseName,
        sets = sets,
        reps = reps,
        toFailure = toFailure,
        advancedTechnique = advancedTechnique,
        orderIndex = orderIndex,
    )
