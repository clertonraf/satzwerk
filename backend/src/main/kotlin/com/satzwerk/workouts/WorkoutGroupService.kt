package com.satzwerk.workouts

import com.satzwerk.common.NotFoundException
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class WorkoutGroupService(
    private val workoutPlanService: WorkoutPlanService,
    private val workoutGroupRepository: WorkoutGroupRepository,
) {
    suspend fun create(
        userId: UUID,
        planId: UUID,
        request: CreateGroupRequest,
    ): WorkoutGroupResponse {
        workoutPlanService.getOwnedPlan(userId, planId)
        return workoutGroupRepository
            .save(
                WorkoutGroup(
                    workoutPlanId = planId,
                    title = request.title,
                    orderIndex = request.orderIndex,
                ),
            ).toResponse()
    }

    suspend fun update(
        userId: UUID,
        planId: UUID,
        groupId: UUID,
        request: UpdateGroupRequest,
    ): WorkoutGroupResponse {
        val existing = getRequiredGroup(userId, planId, groupId)
        return workoutGroupRepository
            .save(
                existing.copy(
                    title = request.title ?: existing.title,
                    orderIndex = request.orderIndex ?: existing.orderIndex,
                    updatedAt = Instant.now(),
                ),
            ).toResponse()
    }

    suspend fun delete(
        userId: UUID,
        planId: UUID,
        groupId: UUID,
    ) {
        val group = getRequiredGroup(userId, planId, groupId)
        workoutGroupRepository.deleteById(requireNotNull(group.id))
    }

    suspend fun getRequiredGroup(
        userId: UUID,
        planId: UUID,
        groupId: UUID,
    ): WorkoutGroup {
        workoutPlanService.getOwnedPlan(userId, planId)
        return workoutGroupRepository.findByIdAndWorkoutPlanId(groupId, planId)
            ?: throw NotFoundException("Workout group not found")
    }
}

