package com.satzwerk.workouts

import com.satzwerk.cache.RedisJsonCacheService
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class ExerciseCatalogCacheTest {
    private val cacheService: RedisJsonCacheService = mock()
    private val exerciseCatalogCache = ExerciseCatalogCache(cacheService)

    @Test
    fun `invalidate retries version bump then falls back to key deletion`(): Unit =
        runBlocking {
            val userId = UUID.randomUUID()
            whenever(cacheService.increment("workouts:exercises:list:version:$userId")).thenReturn(null)
            whenever(cacheService.deleteByPattern("workouts:exercises:list:$userId:v*:*")).thenReturn(true)

            exerciseCatalogCache.invalidateUser(userId)

            verify(cacheService, times(2)).increment("workouts:exercises:list:version:$userId")
            verify(cacheService).deleteByPattern(eq("workouts:exercises:list:$userId:v*:*"))
        }

    @Test
    fun `invalidate logs and continues when version bump and fallback deletion both fail`(): Unit =
        runBlocking {
            val userId = UUID.randomUUID()
            whenever(cacheService.increment("workouts:exercises:list:version:$userId")).thenReturn(null)
            whenever(cacheService.deleteByPattern("workouts:exercises:list:$userId:v*:*")).thenReturn(false)

            exerciseCatalogCache.invalidateUser(userId)

            verify(cacheService, times(2)).increment("workouts:exercises:list:version:$userId")
            verify(cacheService).deleteByPattern(eq("workouts:exercises:list:$userId:v*:*"))
        }
}
