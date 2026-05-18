package com.satzwerk.analytics

import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

@Service
class AnalyticsService(
    private val analyticsRepository: AnalyticsRepository,
) {
    suspend fun heatmap(
        userId: UUID,
        from: LocalDate,
        to: LocalDate,
    ): List<HeatmapEntry> {
        val countsByDate = analyticsRepository.findSetCountsByDate(userId, from, to)

        return from.datesUntil(to.plusDays(1))
            .map { date ->
                val count = countsByDate[date] ?: 0
                HeatmapEntry(date = date, count = count, intensity = intensityTier(count))
            }.toList()
    }

    suspend fun streak(userId: UUID): StreakResponse {
        val days = analyticsRepository.findWorkoutDays(userId)

        if (days.isEmpty()) {
            return StreakResponse(currentStreak = 0, longestStreak = 0)
        }

        val longest = longestStreak(days)
        val today = LocalDate.now(ZoneOffset.UTC)
        val current =
            if (days.first() == today || days.first() == today.minusDays(1)) {
                leadingStreak(days)
            } else {
                0
            }

        return StreakResponse(currentStreak = current, longestStreak = longest)
    }
}
