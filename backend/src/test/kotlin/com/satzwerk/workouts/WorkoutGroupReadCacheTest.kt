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

class WorkoutGroupReadCacheTest {
    private val cacheService: RedisJsonCacheService = mock()
    private val workoutGroupReadCache = WorkoutGroupReadCache(cacheService)

    @Test
    fun `invalidation retries version bump then falls back to key deletion and fresh version write`(): Unit =
        runBlocking {
            val userId = UUID.randomUUID()
            val planId = UUID.randomUUID()
            whenever(cacheService.increment("workouts:groups:version:$userId:$planId")).thenReturn(null)
            whenever(cacheService.deleteByPattern("workouts:groups:$userId:$planId:v*")).thenReturn(true)
            whenever(cacheService.writeFreshVersion("workouts:groups:version:$userId:$planId")).thenReturn(101L)

            workoutGroupReadCache.invalidatePlan(userId, planId)

            verify(cacheService, times(2)).increment("workouts:groups:version:$userId:$planId")
            verify(cacheService).deleteByPattern(eq("workouts:groups:$userId:$planId:v*"))
            verify(cacheService).writeFreshVersion("workouts:groups:version:$userId:$planId")
        }

    @Test
    fun `invalidation logs and continues when version bump and fallback deletion both fail`(): Unit =
        runBlocking {
            val userId = UUID.randomUUID()
            val planId = UUID.randomUUID()
            whenever(cacheService.increment("workouts:groups:version:$userId:$planId")).thenReturn(null)
            whenever(cacheService.deleteByPattern("workouts:groups:$userId:$planId:v*")).thenReturn(false)

            workoutGroupReadCache.invalidatePlan(userId, planId)

            verify(cacheService, times(2)).increment("workouts:groups:version:$userId:$planId")
            verify(cacheService).deleteByPattern(eq("workouts:groups:$userId:$planId:v*"))
        }

    @Test
    fun `fallback invalidation leaves a different version than the stale one`(): Unit =
        runBlocking {
            val userId = UUID.randomUUID()
            val planId = UUID.randomUUID()
            val versionKey = "workouts:groups:version:$userId:$planId"
            val staleVersion = 7L
            val freshVersion = 101L
            whenever(cacheService.getLong(versionKey)).thenReturn(staleVersion, freshVersion)
            whenever(cacheService.increment(versionKey)).thenReturn(null)
            whenever(cacheService.deleteByPattern("workouts:groups:$userId:$planId:v*")).thenReturn(true)
            whenever(cacheService.writeFreshVersion(versionKey)).thenReturn(freshVersion)

            val versionBefore = cacheService.getLong(versionKey)
            workoutGroupReadCache.invalidatePlan(userId, planId)
            val versionAfter = cacheService.getLong(versionKey)

            assertEquals(staleVersion, versionBefore)
            assertEquals(freshVersion, versionAfter)
            assertNotEquals(versionBefore, versionAfter)
        }

    @Test
    fun `delete plan removes payloads and version key`(): Unit =
        runBlocking {
            val userId = UUID.randomUUID()
            val planId = UUID.randomUUID()

            workoutGroupReadCache.deletePlan(userId, planId)

            verify(cacheService).deleteByPattern(eq("workouts:groups:$userId:$planId:v*"))
            verify(cacheService).delete("workouts:groups:version:$userId:$planId")
        }

    @Test
    fun `cache key keeps plans separate within a user`() {
        val userId = UUID.randomUUID()
        val firstPlanId = UUID.randomUUID()
        val secondPlanId = UUID.randomUUID()

        val first = workoutGroupCacheKey(userId, firstPlanId, version = 3)
        val second = workoutGroupCacheKey(userId, secondPlanId, version = 3)

        assertNotEquals(first, second)
    }
}
