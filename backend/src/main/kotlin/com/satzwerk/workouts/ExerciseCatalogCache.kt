package com.satzwerk.workouts

import com.satzwerk.cache.RedisJsonCacheService
import com.satzwerk.cache.get
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.UUID

private const val EXERCISE_CATALOG_CACHE = "exercise-catalog"
private const val EXERCISE_CATALOG_TTL_HOURS = 12L
private val EXERCISE_CATALOG_TTL: Duration = Duration.ofHours(EXERCISE_CATALOG_TTL_HOURS)

@Service
class ExerciseCatalogCache(
    private val cacheService: RedisJsonCacheService,
) {
    suspend fun get(
        userId: UUID,
        muscleGroup: String?,
    ): List<ExerciseResponse>? = cacheService.get(EXERCISE_CATALOG_CACHE, exerciseCatalogCacheKey(userId, muscleGroup))

    suspend fun put(
        userId: UUID,
        muscleGroup: String?,
        exercises: List<ExerciseResponse>,
    ) {
        cacheService.set(
            key = exerciseCatalogCacheKey(userId, muscleGroup),
            value = exercises,
            ttl = EXERCISE_CATALOG_TTL,
        )
    }

    suspend fun invalidateUser(userId: UUID) {
        cacheService.deleteByPattern("workouts:exercises:list:$userId:*")
    }
}

private fun exerciseCatalogCacheKey(
    userId: UUID,
    muscleGroup: String?,
): String = "workouts:exercises:list:$userId:${muscleGroup?.ifBlank { null } ?: "all"}"
