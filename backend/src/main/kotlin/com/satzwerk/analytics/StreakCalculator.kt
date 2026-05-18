package com.satzwerk.analytics

import java.time.LocalDate

internal fun longestStreak(days: List<LocalDate>): Int {
    var longest = 1
    var streak = 1

    for (index in 1 until days.size) {
        if (days[index - 1].minusDays(1) == days[index]) {
            streak += 1
            if (streak > longest) {
                longest = streak
            }
        } else {
            streak = 1
        }
    }

    return longest
}

internal fun leadingStreak(days: List<LocalDate>): Int {
    var streak = 1

    for (index in 1 until days.size) {
        if (days[index - 1].minusDays(1) == days[index]) {
            streak += 1
        } else {
            break
        }
    }

    return streak
}
