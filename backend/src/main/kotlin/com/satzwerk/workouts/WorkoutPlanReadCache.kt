package com.satzwerk.workouts

import com.satzwerk.cache.RedisJsonCacheService
import com.satzwerk.cache.VersionedCacheValue
import com.satzwerk.cache.get
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.UUID

private const val WORKOUT_PLAN_LIST_CACHE = "workout-plan-list"
private const val WORKOUT_PLAN_DETAIL_CACHE = "workout-plan-detail"
private const val WORKOUT_PLAN_READ_TTL_HOURS = 12L
private val WORKOUT_PLAN_READ_TTL: Duration = Duration.ofHours(WORKOUT_PLAN_READ_TTL_HOURS)
private const val WORKOUT_PLAN_LIST_VERSION_KEY = "workouts:plans:list:version"
private const val WORKOUT_PLAN_DETAIL_VERSION_KEY = "workouts:plans:detail:version"

data class WorkoutPlanDetailCacheValue(
    val detailVersion: Long,
    val groupVersion: Long,
    val value: WorkoutPlanDetailResponse?,
)

@Service
class WorkoutPlanReadCache(
    private val cacheService: RedisJsonCacheService,
) {
    private val logger = LoggerFactory.getLogger(WorkoutPlanReadCache::class.java)

    suspend fun lookupList(userId: UUID): VersionedCacheValue<List<WorkoutPlanResponse>> {
        val version = cacheService.getLong(workoutPlanListVersionKey(userId))
        val cached =
            cacheService.get<List<WorkoutPlanResponse>>(
                WORKOUT_PLAN_LIST_CACHE,
                workoutPlanListCacheKey(userId, version),
            )
        return VersionedCacheValue(version = version, value = cached)
    }

    suspend fun putList(
        userId: UUID,
        version: Long,
        plans: List<WorkoutPlanResponse>,
    ) {
        cacheService.set(
            key = workoutPlanListCacheKey(userId, version),
            value = plans,
            ttl = WORKOUT_PLAN_READ_TTL,
        )
    }

    suspend fun lookupDetail(
        userId: UUID,
        planId: UUID,
    ): WorkoutPlanDetailCacheValue {
        val detailVersion = cacheService.getLong(workoutPlanDetailVersionKey(userId, planId))
        val groupVersion = cacheService.getLong(workoutGroupVersionKey(userId, planId))
        val cached =
            cacheService.get<WorkoutPlanDetailResponse>(
                WORKOUT_PLAN_DETAIL_CACHE,
                workoutPlanDetailCacheKey(userId, planId, detailVersion, groupVersion),
            )
        return WorkoutPlanDetailCacheValue(
            detailVersion = detailVersion,
            groupVersion = groupVersion,
            value = cached,
        )
    }

    suspend fun putDetail(
        userId: UUID,
        planId: UUID,
        detailVersion: Long,
        groupVersion: Long,
        detail: WorkoutPlanDetailResponse,
    ) {
        cacheService.set(
            key = workoutPlanDetailCacheKey(userId, planId, detailVersion, groupVersion),
            value = detail,
            ttl = WORKOUT_PLAN_READ_TTL,
        )
    }

    suspend fun invalidateList(userId: UUID) {
        val versionKey = workoutPlanListVersionKey(userId)
        val initialIncrement = cacheService.increment(versionKey)

        if (initialIncrement != null) {
            return
        }

        if (cacheService.increment(versionKey) != null) {
            logger.warn("WorkoutPlan list cache invalidation recovered on retry for userId={}", userId)
        } else if (!cacheService.deleteByPattern(workoutPlanListCachePattern(userId))) {
            logger.error(
                "WorkoutPlan list cache invalidation failed after retry and fallback deletion for userId={}; " +
                    "stale data may persist until TTL expiry",
                userId,
            )
        } else if (cacheService.writeFreshVersion(versionKey) == null) {
            logger.warn(
                "WorkoutPlan list cache invalidation fell back to direct key deletion for userId={} " +
                    "but could not persist a fresh version; stale data may persist until TTL expiry",
                userId,
            )
        } else {
            logger.warn(
                "WorkoutPlan list cache invalidation fell back to direct key deletion and fresh version write " +
                    "for userId={}",
                userId,
            )
        }
    }

    suspend fun invalidateDetail(
        userId: UUID,
        planId: UUID,
    ) {
        val versionKey = workoutPlanDetailVersionKey(userId, planId)
        val initialIncrement = cacheService.increment(versionKey)

        if (initialIncrement != null) {
            return
        }

        if (cacheService.increment(versionKey) != null) {
            logger.warn(
                "WorkoutPlan detail cache invalidation recovered on retry for userId={} planId={}",
                userId,
                planId,
            )
        } else if (!cacheService.deleteByPattern(workoutPlanDetailCachePattern(userId, planId))) {
            logger.error(
                "WorkoutPlan detail cache invalidation failed after retry and fallback deletion for userId={} " +
                    "planId={}; stale data may persist until TTL expiry",
                userId,
                planId,
            )
        } else if (cacheService.writeFreshVersion(versionKey) == null) {
            logger.warn(
                "WorkoutPlan detail cache invalidation fell back to direct key deletion for userId={} " +
                    "planId={} but could not persist a fresh version; stale data may persist until TTL expiry",
                userId,
                planId,
            )
        } else {
            logger.warn(
                "WorkoutPlan detail cache invalidation fell back to direct key deletion and fresh version write " +
                    "for userId={} planId={}",
                userId,
                planId,
            )
        }
    }
}

internal fun workoutPlanListCacheKey(
    userId: UUID,
    version: Long,
): String = "workouts:plans:list:$userId:v$version"

internal fun workoutPlanDetailCacheKey(
    userId: UUID,
    planId: UUID,
    detailVersion: Long,
    groupVersion: Long,
): String = "workouts:plans:detail:$userId:$planId:v$detailVersion:g$groupVersion"

private fun workoutPlanListCachePattern(userId: UUID): String = "workouts:plans:list:$userId:v*"

private fun workoutPlanDetailCachePattern(
    userId: UUID,
    planId: UUID,
): String = "workouts:plans:detail:$userId:$planId:v*"

private fun workoutPlanListVersionKey(userId: UUID): String = "$WORKOUT_PLAN_LIST_VERSION_KEY:$userId"

private fun workoutPlanDetailVersionKey(
    userId: UUID,
    planId: UUID,
): String = "$WORKOUT_PLAN_DETAIL_VERSION_KEY:$userId:$planId"
