package com.satzwerk.analytics

import java.time.LocalDate

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

fun intensityTier(count: Int): Int =
    when {
        count == NO_INTENSITY -> NO_INTENSITY
        else -> minOf(MAX_INTENSITY, ((count - 1) / TIER_STEP) + 1)
    }
