package com.satzwerk.analytics

import java.time.LocalDate

private const val NO_INTENSITY = 0
private const val LOW_INTENSITY = 1
private const val MEDIUM_INTENSITY = 2
private const val HIGH_INTENSITY = 3
private const val MAX_INTENSITY = 4
private const val TIER_ONE_MAX = 4
private const val TIER_TWO_MAX = 9
private const val TIER_THREE_MAX = 14

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
        count <= TIER_ONE_MAX -> LOW_INTENSITY
        count <= TIER_TWO_MAX -> MEDIUM_INTENSITY
        count <= TIER_THREE_MAX -> HIGH_INTENSITY
        else -> MAX_INTENSITY
    }
