package com.satzwerk.analytics

import com.satzwerk.cache.RedisJsonCacheService
import com.satzwerk.cache.get
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.LocalDate
import java.util.UUID

private const val ANALYTICS_HEATMAP_CACHE = "analytics-heatmap"
private const val ANALYTICS_STREAK_CACHE = "analytics-streak"
private const val ANALYTICS_READ_TTL_SECONDS = 45L
private val ANALYTICS_READ_TTL: Duration = Duration.ofSeconds(ANALYTICS_READ_TTL_SECONDS)

@Service
class AnalyticsReadCache(
    private val cacheService: RedisJsonCacheService,
) {
    suspend fun getHeatmap(
        userId: UUID,
        from: LocalDate,
        to: LocalDate,
    ): List<HeatmapEntry>? = cacheService.get(ANALYTICS_HEATMAP_CACHE, heatmapCacheKey(userId, from, to))

    suspend fun putHeatmap(
        userId: UUID,
        from: LocalDate,
        to: LocalDate,
        entries: List<HeatmapEntry>,
    ) {
        cacheService.set(
            key = heatmapCacheKey(userId, from, to),
            value = entries,
            ttl = ANALYTICS_READ_TTL,
        )
    }

    suspend fun getStreak(userId: UUID): StreakResponse? =
        cacheService.get(ANALYTICS_STREAK_CACHE, streakCacheKey(userId))

    suspend fun putStreak(
        userId: UUID,
        streak: StreakResponse,
    ) {
        cacheService.set(
            key = streakCacheKey(userId),
            value = streak,
            ttl = ANALYTICS_READ_TTL,
        )
    }

    suspend fun invalidateUser(userId: UUID) {
        cacheService.deleteByPattern("analytics:heatmap:$userId:*")
        cacheService.delete(streakCacheKey(userId))
    }
}

private fun heatmapCacheKey(
    userId: UUID,
    from: LocalDate,
    to: LocalDate,
): String = "analytics:heatmap:$userId:$from:$to"

private fun streakCacheKey(userId: UUID): String = "analytics:streak:$userId"
