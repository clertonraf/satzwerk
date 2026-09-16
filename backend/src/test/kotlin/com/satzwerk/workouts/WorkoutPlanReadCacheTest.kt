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

class WorkoutPlanReadCacheTest {
    private val cacheService: RedisJsonCacheService = mock()
    private val workoutPlanReadCache = WorkoutPlanReadCache(cacheService)

    @Test
    fun `list invalidation retries version bump then falls back to key deletion and fresh version write`(): Unit =
        runBlocking {
            val userId = UUID.randomUUID()
            whenever(cacheService.increment("workouts:plans:list:version:$userId")).thenReturn(null)
            whenever(cacheService.deleteByPattern("workouts:plans:list:$userId:v*")).thenReturn(true)
            whenever(cacheService.writeFreshVersion("workouts:plans:list:version:$userId")).thenReturn(101L)

            workoutPlanReadCache.invalidateList(userId)

            verify(cacheService, times(2)).increment("workouts:plans:list:version:$userId")
            verify(cacheService).deleteByPattern(eq("workouts:plans:list:$userId:v*"))
            verify(cacheService).writeFreshVersion("workouts:plans:list:version:$userId")
        }

    @Test
    fun `detail invalidation retries version bump then falls back to key deletion and fresh version write`(): Unit =
        runBlocking {
            val userId = UUID.randomUUID()
            val planId = UUID.randomUUID()
            whenever(cacheService.increment("workouts:plans:detail:version:$userId:$planId")).thenReturn(null)
            whenever(cacheService.deleteByPattern("workouts:plans:detail:$userId:$planId:v*")).thenReturn(true)
            whenever(cacheService.writeFreshVersion("workouts:plans:detail:version:$userId:$planId")).thenReturn(101L)

            workoutPlanReadCache.invalidateDetail(userId, planId)

            verify(cacheService, times(2)).increment("workouts:plans:detail:version:$userId:$planId")
            verify(cacheService).deleteByPattern(eq("workouts:plans:detail:$userId:$planId:v*"))
            verify(cacheService).writeFreshVersion("workouts:plans:detail:version:$userId:$planId")
        }

    @Test
    fun `fallback detail invalidation leaves a different version than the stale one`(): Unit =
        runBlocking {
            val userId = UUID.randomUUID()
            val planId = UUID.randomUUID()
            val versionKey = "workouts:plans:detail:version:$userId:$planId"
            val staleVersion = 7L
            val freshVersion = 101L
            whenever(cacheService.getLong(versionKey)).thenReturn(staleVersion, freshVersion)
            whenever(cacheService.increment(versionKey)).thenReturn(null)
            whenever(cacheService.deleteByPattern("workouts:plans:detail:$userId:$planId:v*")).thenReturn(true)
            whenever(cacheService.writeFreshVersion(versionKey)).thenReturn(freshVersion)

            val versionBefore = cacheService.getLong(versionKey)
            workoutPlanReadCache.invalidateDetail(userId, planId)
            val versionAfter = cacheService.getLong(versionKey)

            assertEquals(staleVersion, versionBefore)
            assertEquals(freshVersion, versionAfter)
            assertNotEquals(versionBefore, versionAfter)
        }

    @Test
    fun `delete detail removes payloads and version key`(): Unit =
        runBlocking {
            val userId = UUID.randomUUID()
            val planId = UUID.randomUUID()

            workoutPlanReadCache.deleteDetail(userId, planId)

            verify(cacheService).deleteByPattern(eq("workouts:plans:detail:$userId:$planId:v*"))
            verify(cacheService).delete("workouts:plans:detail:version:$userId:$planId")
        }

    @Test
    fun `detail cache key keeps plans separate within a user`() {
        val userId = UUID.randomUUID()
        val firstPlanId = UUID.randomUUID()
        val secondPlanId = UUID.randomUUID()

        val first = workoutPlanDetailCacheKey(userId, firstPlanId, detailVersion = 3, groupVersion = 5)
        val second = workoutPlanDetailCacheKey(userId, secondPlanId, detailVersion = 3, groupVersion = 5)

        assertNotEquals(first, second)
    }

    @Test
    fun `detail cache key keeps detail versions separate for the same plan`() {
        val userId = UUID.randomUUID()
        val planId = UUID.randomUUID()

        val stale = workoutPlanDetailCacheKey(userId, planId, detailVersion = 3, groupVersion = 5)
        val fresh = workoutPlanDetailCacheKey(userId, planId, detailVersion = 4, groupVersion = 5)

        assertNotEquals(stale, fresh)
    }

    @Test
    fun `detail cache key keeps group versions separate for the same plan`() {
        val userId = UUID.randomUUID()
        val planId = UUID.randomUUID()

        val stale = workoutPlanDetailCacheKey(userId, planId, detailVersion = 3, groupVersion = 5)
        val fresh = workoutPlanDetailCacheKey(userId, planId, detailVersion = 3, groupVersion = 6)

        assertNotEquals(stale, fresh)
    }
}
