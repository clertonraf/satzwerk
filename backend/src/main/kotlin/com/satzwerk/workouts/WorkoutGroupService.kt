package com.satzwerk.workouts

import com.satzwerk.analytics.AnalyticsReadCache
import com.satzwerk.common.TransactionRunner
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class WorkoutGroupService(
    private val workoutPlanService: WorkoutPlanService,
    private val workoutGroupRepository: WorkoutGroupRepository,
    private val workoutGroupReadCache: WorkoutGroupReadCache,
    private val analyticsReadCache: AnalyticsReadCache,
    private val transactionRunner: TransactionRunner,
) {
    suspend fun create(
        userId: UUID,
        planId: UUID,
        request: CreateGroupRequest,
    ): WorkoutGroupResponse {
        workoutPlanService.getRequiredPlan(userId, planId)
        return workoutGroupRepository
            .save(
                WorkoutGroup(
                    workoutPlanId = planId,
                    title = request.title,
                    orderIndex = request.orderIndex,
                ),
            ).also {
                transactionRunner.afterCommit {
                    workoutGroupReadCache.invalidatePlan(userId, planId)
                }
            }.let(WorkoutGroupResponse::from)
    }

    suspend fun update(
        userId: UUID,
        planId: UUID,
        groupId: UUID,
        request: UpdateGroupRequest,
    ): WorkoutGroupResponse {
        val existing = workoutPlanService.getRequiredGroup(userId, planId, groupId)
        return workoutGroupRepository
            .save(
                existing.copy(
                    title = request.title ?: existing.title,
                    orderIndex = request.orderIndex ?: existing.orderIndex,
                    updatedAt = Instant.now(),
                ),
            ).also {
                transactionRunner.afterCommit {
                    workoutGroupReadCache.invalidatePlan(userId, planId)
                }
            }.let(WorkoutGroupResponse::from)
    }

    suspend fun delete(
        userId: UUID,
        planId: UUID,
        groupId: UUID,
    ) {
        val group = workoutPlanService.getRequiredGroup(userId, planId, groupId)
        workoutGroupRepository.deleteById(requireNotNull(group.id))
        transactionRunner.afterCommit {
            workoutGroupReadCache.invalidatePlan(userId, planId)
            analyticsReadCache.invalidateUser(userId)
        }
    }
}
