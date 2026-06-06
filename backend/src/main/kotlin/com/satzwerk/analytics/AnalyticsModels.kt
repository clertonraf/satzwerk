package com.satzwerk.analytics

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private const val NO_INTENSITY = 0
private const val MAX_INTENSITY = 10
private const val TIER_STEP = 4

data class HeatmapEntry(
    val date: LocalDate,
    val count: Int,
    val intensity: Int,
)

data class StreakResponse(
    val currentStreak: Int,
    val longestStreak: Int,
)

data class DashboardSummary(
    val currentStreak: Int,
    val longestStreak: Int,
    val sessionsThisMonth: Int,
    val setsThisWeek: Int,
    val totalSessions: Int,
    val prsThisMonth: Int,
    val activePlanDays: Int?,
)

data class WeeklyTrendEntry(
    val week: String,
    val setCount: Int,
    val sessionCount: Int,
)

data class PersonalRecord(
    val exerciseId: UUID,
    val exerciseName: String,
    val weightKg: BigDecimal,
    val achievedAt: Instant,
)

fun intensityTier(count: Int): Int =
    when {
        count == NO_INTENSITY -> NO_INTENSITY
        else -> minOf(MAX_INTENSITY, ((count - 1) / TIER_STEP) + 1)
    }
