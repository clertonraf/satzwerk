package com.satzwerk.analytics

import com.satzwerk.cache.RedisJsonCacheService
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class AnalyticsReadCacheTest {
    private val cacheService: RedisJsonCacheService = mock()
    private val analyticsReadCache = AnalyticsReadCache(cacheService)

    @Test
    fun `invalidate retries version bump then falls back to key deletion`(): Unit =
        runBlocking {
            val userId = UUID.randomUUID()
            whenever(cacheService.increment("analytics:version:$userId")).thenReturn(null)
            whenever(cacheService.deleteByPattern("analytics:heatmap:$userId:v*:*:*")).thenReturn(true)
            whenever(cacheService.deleteByPattern("analytics:streak:$userId:v*")).thenReturn(true)

            analyticsReadCache.invalidateUser(userId)

            verify(cacheService, times(2)).increment("analytics:version:$userId")
            verify(cacheService).deleteByPattern(eq("analytics:heatmap:$userId:v*:*:*"))
            verify(cacheService).deleteByPattern(eq("analytics:streak:$userId:v*"))
        }

    @Test
    fun `invalidate logs and continues when version bump and fallback deletion both fail`(): Unit =
        runBlocking {
            val userId = UUID.randomUUID()
            whenever(cacheService.increment("analytics:version:$userId")).thenReturn(null)
            whenever(cacheService.deleteByPattern("analytics:heatmap:$userId:v*:*:*")).thenReturn(false)
            whenever(cacheService.deleteByPattern("analytics:streak:$userId:v*")).thenReturn(false)

            analyticsReadCache.invalidateUser(userId)

            verify(cacheService, times(2)).increment("analytics:version:$userId")
            verify(cacheService).deleteByPattern(eq("analytics:heatmap:$userId:v*:*:*"))
            verify(cacheService).deleteByPattern(eq("analytics:streak:$userId:v*"))
        }
}
