package com.satzwerk.workouts

import com.satzwerk.cache.RedisJsonCacheService
import com.satzwerk.cache.VersionedCacheValue
import com.satzwerk.cache.get
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.UUID

private const val WORKOUT_GROUP_CACHE = "workout-groups"
private const val WORKOUT_GROUP_READ_TTL_HOURS = 12L
private val WORKOUT_GROUP_READ_TTL: Duration = Duration.ofHours(WORKOUT_GROUP_READ_TTL_HOURS)
private const val WORKOUT_GROUP_VERSION_KEY = "workouts:groups:version"

@Service
class WorkoutGroupReadCache(
    private val cacheService: RedisJsonCacheService,
) {
    private val logger = LoggerFactory.getLogger(WorkoutGroupReadCache::class.java)

    suspend fun currentVersion(
        userId: UUID,
        planId: UUID,
    ): Long = cacheService.getLong(workoutGroupVersionKey(userId, planId))

    suspend fun lookup(
        userId: UUID,
        planId: UUID,
    ): VersionedCacheValue<List<WorkoutGroup>> {
        val version = currentVersion(userId, planId)
        val cached =
            cacheService.get<List<WorkoutGroup>>(
                WORKOUT_GROUP_CACHE,
                workoutGroupCacheKey(userId, planId, version),
            )
        return VersionedCacheValue(version = version, value = cached)
    }

    suspend fun lookup(
        userId: UUID,
        planId: UUID,
        version: Long,
    ): List<WorkoutGroup>? {
        val cached =
            cacheService.get<List<WorkoutGroup>>(
                WORKOUT_GROUP_CACHE,
                workoutGroupCacheKey(userId, planId, version),
            )
        return cached
    }

    suspend fun put(
        userId: UUID,
        planId: UUID,
        version: Long,
        groups: List<WorkoutGroup>,
    ) {
        cacheService.set(
            key = workoutGroupCacheKey(userId, planId, version),
            value = groups,
            ttl = WORKOUT_GROUP_READ_TTL,
        )
    }

    suspend fun invalidatePlan(
        userId: UUID,
        planId: UUID,
    ) {
        val versionKey = workoutGroupVersionKey(userId, planId)
        val initialIncrement = cacheService.increment(versionKey)

        if (initialIncrement != null) {
            return
        }

        if (cacheService.increment(versionKey) != null) {
            logger.warn(
                "WorkoutGroup cache invalidation recovered on retry for userId={} planId={}",
                userId,
                planId,
            )
        } else if (!cacheService.deleteByPattern(workoutGroupCachePattern(userId, planId))) {
            logger.error(
                "WorkoutGroup cache invalidation failed after retry and fallback deletion for userId={} " +
                    "planId={}; stale data may persist until TTL expiry",
                userId,
                planId,
            )
        } else if (cacheService.writeFreshVersion(versionKey) == null) {
            logger.warn(
                "WorkoutGroup cache invalidation fell back to direct key deletion for userId={} " +
                    "planId={} but could not persist a fresh version; stale data may persist until TTL expiry",
                userId,
                planId,
            )
        } else {
            logger.warn(
                "WorkoutGroup cache invalidation fell back to direct key deletion and fresh version write " +
                    "for userId={} planId={}",
                userId,
                planId,
            )
        }
    }

    suspend fun deletePlan(
        userId: UUID,
        planId: UUID,
    ) {
        cacheService.deleteByPattern(workoutGroupCachePattern(userId, planId))
        cacheService.delete(workoutGroupVersionKey(userId, planId))
    }
}

internal fun workoutGroupCacheKey(
    userId: UUID,
    planId: UUID,
    version: Long,
): String = "workouts:groups:$userId:$planId:v$version"

private fun workoutGroupCachePattern(
    userId: UUID,
    planId: UUID,
): String = "workouts:groups:$userId:$planId:v*"

internal fun workoutGroupVersionKey(
    userId: UUID,
    planId: UUID,
): String = "$WORKOUT_GROUP_VERSION_KEY:$userId:$planId"
