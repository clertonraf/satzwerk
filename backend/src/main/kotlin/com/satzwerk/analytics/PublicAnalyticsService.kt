package com.satzwerk.analytics

import com.satzwerk.workouts.WorkoutReadPort
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.util.UUID

@Service
class PublicAnalyticsService(
    private val workoutReadPort: WorkoutReadPort,
    private val analyticsReadCache: AnalyticsReadCache,
) {
    suspend fun heatmap(
        userId: UUID,
        from: LocalDate,
        to: LocalDate,
    ): List<HeatmapEntry> {
        val cached = analyticsReadCache.lookupHeatmap(userId, from, to)
        return cached.value
            ?: loadHeatmap(userId, from, to).also {
                analyticsReadCache.putHeatmap(userId, from, to, cached.version, it)
            }
    }

    private suspend fun loadHeatmap(
        userId: UUID,
        from: LocalDate,
        to: LocalDate,
    ): List<HeatmapEntry> {
        val countsByDate = workoutReadPort.findSetCountsByDate(userId, from, to)

        return from.datesUntil(to.plusDays(1))
            .map { date ->
                val count = countsByDate[date] ?: 0
                HeatmapEntry(date = date, count = count, intensity = intensityTier(count))
            }.toList()
    }
}
