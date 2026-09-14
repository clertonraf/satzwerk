package com.satzwerk.analytics

import com.satzwerk.cache.RedisJsonCacheService
import com.satzwerk.cache.VersionedCacheValue
import com.satzwerk.cache.get
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.LocalDate
import java.util.UUID

private const val ANALYTICS_HEATMAP_CACHE = "analytics-heatmap"
private const val ANALYTICS_STREAK_CACHE = "analytics-streak"
private const val ANALYTICS_READ_TTL_SECONDS = 45L
private val ANALYTICS_READ_TTL: Duration = Duration.ofSeconds(ANALYTICS_READ_TTL_SECONDS)
private const val ANALYTICS_VERSION_KEY = "analytics:version"

@Service
class AnalyticsReadCache(
    private val cacheService: RedisJsonCacheService,
) {
    private val logger = LoggerFactory.getLogger(AnalyticsReadCache::class.java)

    suspend fun lookupHeatmap(
        userId: UUID,
        from: LocalDate,
        to: LocalDate,
    ): VersionedCacheValue<List<HeatmapEntry>> {
        val version = cacheService.getLong(analyticsVersionKey(userId))
        val cached =
            cacheService.get<List<HeatmapEntry>>(
                ANALYTICS_HEATMAP_CACHE,
                heatmapCacheKey(userId, from, to, version),
            )
        return VersionedCacheValue(version = version, value = cached)
    }

    suspend fun putHeatmap(
        userId: UUID,
        from: LocalDate,
        to: LocalDate,
        version: Long,
        entries: List<HeatmapEntry>,
    ) {
        cacheService.set(
            key = heatmapCacheKey(userId, from, to, version),
            value = entries,
            ttl = ANALYTICS_READ_TTL,
        )
    }

    suspend fun lookupStreak(userId: UUID): VersionedCacheValue<StreakResponse> {
        val version = cacheService.getLong(analyticsVersionKey(userId))
        val cached = cacheService.get<StreakResponse>(ANALYTICS_STREAK_CACHE, streakCacheKey(userId, version))
        return VersionedCacheValue(version = version, value = cached)
    }

    suspend fun putStreak(
        userId: UUID,
        version: Long,
        streak: StreakResponse,
    ) {
        cacheService.set(
            key = streakCacheKey(userId, version),
            value = streak,
            ttl = ANALYTICS_READ_TTL,
        )
    }

    suspend fun invalidateUser(userId: UUID) {
        val versionKey = analyticsVersionKey(userId)
        val initialIncrement = cacheService.increment(versionKey)

        if (initialIncrement != null) {
            return
        }

        if (cacheService.increment(versionKey) != null) {
            logger.warn("Analytics cache invalidation recovered on retry for userId={}", userId)
        } else {
            val deletedHeatmap = cacheService.deleteByPattern(heatmapCachePattern(userId))
            val deletedStreak = cacheService.deleteByPattern(streakCachePattern(userId))
            if (!deletedHeatmap || !deletedStreak) {
                logger.error(
                    "Analytics cache invalidation failed after retry and fallback deletion for userId={}; " +
                        "stale data may persist until TTL expiry",
                    userId,
                )
            } else {
                logger.warn("Analytics cache invalidation fell back to direct key deletion for userId={}", userId)
            }
        }
    }
}

private fun heatmapCacheKey(
    userId: UUID,
    from: LocalDate,
    to: LocalDate,
    version: Long,
): String = "analytics:heatmap:$userId:v$version:$from:$to"

private fun streakCacheKey(
    userId: UUID,
    version: Long,
): String = "analytics:streak:$userId:v$version"

private fun heatmapCachePattern(userId: UUID): String = "analytics:heatmap:$userId:v*:*:*"

private fun streakCachePattern(userId: UUID): String = "analytics:streak:$userId:v*"

private fun analyticsVersionKey(userId: UUID): String = "$ANALYTICS_VERSION_KEY:$userId"
