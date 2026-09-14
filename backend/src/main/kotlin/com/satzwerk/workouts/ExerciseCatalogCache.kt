package com.satzwerk.workouts

import com.satzwerk.cache.CacheInvalidationException
import com.satzwerk.cache.RedisJsonCacheService
import com.satzwerk.cache.VersionedCacheValue
import com.satzwerk.cache.get
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.UUID

private const val EXERCISE_CATALOG_CACHE = "exercise-catalog"
private const val EXERCISE_CATALOG_TTL_HOURS = 12L
private val EXERCISE_CATALOG_TTL: Duration = Duration.ofHours(EXERCISE_CATALOG_TTL_HOURS)
private const val EXERCISE_CATALOG_VERSION_KEY = "workouts:exercises:list:version"

@Service
class ExerciseCatalogCache(
    private val cacheService: RedisJsonCacheService,
) {
    private val logger = LoggerFactory.getLogger(ExerciseCatalogCache::class.java)

    suspend fun lookup(
        userId: UUID,
        muscleGroup: String?,
    ): VersionedCacheValue<List<ExerciseResponse>> {
        val version = cacheService.getLong(exerciseCatalogVersionKey(userId))
        val cached =
            cacheService.get<List<ExerciseResponse>>(
                EXERCISE_CATALOG_CACHE,
                exerciseCatalogCacheKey(userId, muscleGroup, version),
            )
        return VersionedCacheValue(version = version, value = cached)
    }

    suspend fun put(
        userId: UUID,
        muscleGroup: String?,
        version: Long,
        exercises: List<ExerciseResponse>,
    ) {
        cacheService.set(
            key = exerciseCatalogCacheKey(userId, muscleGroup, version),
            value = exercises,
            ttl = EXERCISE_CATALOG_TTL,
        )
    }

    suspend fun invalidateUser(userId: UUID) {
        if (cacheService.increment(exerciseCatalogVersionKey(userId)) != null) {
            return
        }
        if (cacheService.increment(exerciseCatalogVersionKey(userId)) != null) {
            logger.warn("Exercise catalog cache invalidation recovered on retry for userId={}", userId)
            return
        }

        if (!cacheService.deleteByPattern(exerciseCatalogCachePattern(userId))) {
            throw CacheInvalidationException("Exercise catalog cache invalidation failed for userId=$userId")
        }
        logger.warn("Exercise catalog cache invalidation fell back to direct key deletion for userId={}", userId)
    }
}

private fun exerciseCatalogCacheKey(
    userId: UUID,
    muscleGroup: String?,
    version: Long,
): String = "workouts:exercises:list:$userId:v$version:${muscleGroup?.ifBlank { null } ?: "all"}"

private fun exerciseCatalogCachePattern(userId: UUID): String = "workouts:exercises:list:$userId:v*:*"

private fun exerciseCatalogVersionKey(userId: UUID): String = "$EXERCISE_CATALOG_VERSION_KEY:$userId"
