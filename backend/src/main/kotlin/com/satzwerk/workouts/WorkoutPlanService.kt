package com.satzwerk.workouts

import com.satzwerk.analytics.AnalyticsReadCache
import com.satzwerk.common.ConflictException
import com.satzwerk.common.NotFoundException
import com.satzwerk.common.Owned
import com.satzwerk.common.TransactionRunner
import com.satzwerk.common.assertOwner
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class WorkoutPlanService(
    private val workoutPlanRepository: WorkoutPlanRepository,
    private val workoutPlanDetailDeps: WorkoutPlanDetailDeps,
    private val workoutReadCaches: WorkoutReadCaches,
    private val analyticsReadCache: AnalyticsReadCache,
    private val transactionRunner: TransactionRunner,
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
            ).also {
                transactionRunner.afterCommit {
                    workoutReadCaches.workoutPlanReadCache.invalidateList(userId)
                }
            }.let(WorkoutPlanResponse::from)

    suspend fun list(userId: UUID): List<WorkoutPlanResponse> =
        workoutReadCaches.workoutPlanReadCache.lookupList(userId).let { cached ->
            cached.value
                ?: loadWorkoutPlanList(userId, workoutPlanRepository).also { plans ->
                    workoutReadCaches.workoutPlanReadCache.putList(userId, cached.version, plans)
                }
        }

    suspend fun getDetail(
        userId: UUID,
        planId: UUID,
    ): WorkoutPlanDetailResponse =
        workoutReadCaches.workoutPlanReadCache.lookupDetail(userId, planId).let { cached ->
            cached.value
                ?: loadWorkoutPlanDetail(
                    context =
                        WorkoutPlanDetailCacheContext(
                            userId = userId,
                            planId = planId,
                            groupVersion = cached.groupVersion,
                        ),
                    workoutPlanRepository = workoutPlanRepository,
                    workoutPlanDetailDeps = workoutPlanDetailDeps,
                    workoutReadCaches = workoutReadCaches,
                ).also { detail ->
                    workoutReadCaches.workoutPlanReadCache.putDetail(
                        userId = userId,
                        planId = planId,
                        detailVersion = cached.detailVersion,
                        groupVersion = cached.groupVersion,
                        detail = detail,
                    )
                }
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
        transactionRunner.afterCommit {
            workoutReadCaches.workoutPlanReadCache.invalidateList(userId)
            workoutReadCaches.workoutPlanReadCache.invalidateDetail(userId, planId)
        }

        return WorkoutPlanResponse.from(updated)
    }

    suspend fun delete(
        userId: UUID,
        planId: UUID,
    ) {
        val plan = getRequiredPlan(userId, planId)
        workoutPlanRepository.deleteById(requireNotNull(plan.id))
        transactionRunner.afterCommit {
            workoutReadCaches.workoutPlanReadCache.invalidateList(userId)
            workoutReadCaches.workoutPlanReadCache.deleteDetail(userId, planId)
            workoutReadCaches.workoutGroupReadCache.deletePlan(userId, planId)
            analyticsReadCache.invalidateUser(userId)
        }
    }

    @Transactional
    suspend fun activate(
        userId: UUID,
        planId: UUID,
    ) {
        val plan = getRequiredPlan(userId, planId)
        val now = Instant.now()
        val previouslyActivePlanIds =
            workoutPlanRepository.findAllByUserIdAndIsActive(userId, true)
                .filter { it.id != plan.id }
                .map { activePlan ->
                    workoutPlanRepository.save(
                        activePlan.copy(isActive = false, activatedAt = null, updatedAt = now),
                    )
                    requireNotNull(activePlan.id)
                }
        try {
            workoutPlanRepository.save(plan.copy(isActive = true, activatedAt = now, updatedAt = now))
        } catch (_: DataIntegrityViolationException) {
            throw ConflictException("Another WorkoutPlan was activated concurrently")
        }
        transactionRunner.afterCommit {
            workoutReadCaches.workoutPlanReadCache.invalidateList(userId)
            workoutReadCaches.workoutPlanReadCache.invalidateDetail(userId, planId)
            previouslyActivePlanIds.forEach { activePlanId ->
                workoutReadCaches.workoutPlanReadCache.invalidateDetail(userId, activePlanId)
            }
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
        return workoutPlanDetailDeps.workoutGroupRepository.findByIdAndWorkoutPlanId(groupId, planId)
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

private suspend fun loadWorkoutPlanList(
    userId: UUID,
    workoutPlanRepository: WorkoutPlanRepository,
): List<WorkoutPlanResponse> =
    workoutPlanRepository.findAllByUserId(userId)
        .sortedBy(WorkoutPlan::createdAt)
        .map(WorkoutPlanResponse::from)

private suspend fun loadWorkoutPlanDetail(
    context: WorkoutPlanDetailCacheContext,
    workoutPlanRepository: WorkoutPlanRepository,
    workoutPlanDetailDeps: WorkoutPlanDetailDeps,
    workoutReadCaches: WorkoutReadCaches,
): WorkoutPlanDetailResponse {
    val plan =
        fetchPlanAsOwned(context.planId, workoutPlanRepository).assertOwner(context.userId, "Workout plan").value
    val groups =
        loadWorkoutGroups(
            userId = context.userId,
            planId = context.planId,
            groupVersion = context.groupVersion,
            workoutGroupRepository = workoutPlanDetailDeps.workoutGroupRepository,
            workoutGroupReadCache = workoutReadCaches.workoutGroupReadCache,
        )
    val groupIds = groups.mapNotNull(WorkoutGroup::id)
    val workoutExercises =
        if (groupIds.isEmpty()) {
            emptyList<WorkoutExercise>()
        } else {
            workoutPlanDetailDeps.workoutExerciseRepository
                .findAllByWorkoutGroupIdInOrderByWorkoutGroupIdAscOrderIndexAsc(groupIds)
        }
    val exerciseNamesById =
        loadExerciseNames(
            workoutExercises.map(WorkoutExercise::exerciseId).toSet(),
            workoutPlanDetailDeps.exerciseRepository,
        )
    val exercisesByGroup =
        workoutExercises
            .groupBy(WorkoutExercise::workoutGroupId)
            .mapValues { (_, exercises) -> toWorkoutExerciseResponses(exercises, exerciseNamesById) }

    return WorkoutPlanDetailResponse.from(plan, groups, exercisesByGroup)
}

private data class WorkoutPlanDetailCacheContext(
    val userId: UUID,
    val planId: UUID,
    val groupVersion: Long,
)

private suspend fun loadWorkoutGroups(
    userId: UUID,
    planId: UUID,
    groupVersion: Long,
    workoutGroupRepository: WorkoutGroupRepository,
    workoutGroupReadCache: WorkoutGroupReadCache,
): List<WorkoutGroup> =
    workoutGroupReadCache.lookup(userId, planId, groupVersion)
        ?: workoutGroupRepository.findAllByWorkoutPlanIdOrderByOrderIndex(planId).also { groups ->
            workoutGroupReadCache.put(userId, planId, groupVersion, groups)
        }
