package com.satzwerk.workouts

import com.satzwerk.cache.RedisJsonCacheService
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
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
    fun `invalidate retries version bump then falls back to key deletion and fresh version write`(): Unit =
        runBlocking {
            val userId = UUID.randomUUID()
            whenever(cacheService.increment("workouts:exercises:list:version:$userId")).thenReturn(null)
            whenever(cacheService.deleteByPattern("workouts:exercises:list:$userId:v*:*")).thenReturn(true)
            whenever(cacheService.writeFreshVersion("workouts:exercises:list:version:$userId")).thenReturn(101L)

            exerciseCatalogCache.invalidateUser(userId)

            verify(cacheService, times(2)).increment("workouts:exercises:list:version:$userId")
            verify(cacheService).deleteByPattern(eq("workouts:exercises:list:$userId:v*:*"))
            verify(cacheService).writeFreshVersion("workouts:exercises:list:version:$userId")
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

    @Test
    fun `fallback invalidation leaves a different version than the stale one`(): Unit =
        runBlocking {
            val userId = UUID.randomUUID()
            val versionKey = "workouts:exercises:list:version:$userId"
            val staleVersion = 7L
            val freshVersion = 101L
            whenever(cacheService.getLong(versionKey)).thenReturn(staleVersion, freshVersion)
            whenever(cacheService.increment(versionKey)).thenReturn(null)
            whenever(cacheService.deleteByPattern("workouts:exercises:list:$userId:v*:*")).thenReturn(true)
            whenever(cacheService.writeFreshVersion(versionKey)).thenReturn(freshVersion)

            val versionBefore = cacheService.getLong(versionKey)
            exerciseCatalogCache.invalidateUser(userId)
            val versionAfter = cacheService.getLong(versionKey)

            assertEquals(staleVersion, versionBefore)
            assertEquals(freshVersion, versionAfter)
            assertNotEquals(versionBefore, versionAfter)
        }

    @Test
    fun `cache key normalizes case and whitespace variants of muscleGroup`() {
        val userId = UUID.randomUUID()
        val trimmedUpper = exerciseCatalogCacheKey(userId, "  CHEST  ", version = 3)
        val lowercase = exerciseCatalogCacheKey(userId, "chest", version = 3)

        assertEquals(trimmedUpper, lowercase)
    }
}
