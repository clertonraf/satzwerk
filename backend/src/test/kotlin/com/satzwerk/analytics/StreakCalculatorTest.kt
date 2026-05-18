package com.satzwerk.analytics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class StreakCalculatorTest {
    private val today = LocalDate.of(2024, 6, 15)

    @Test
    fun `longestStreak returns 1 for single day`() {
        assertEquals(1, longestStreak(listOf(today)))
    }

    @Test
    fun `longestStreak returns length of unbroken run`() {
        val days = listOf(today, today.minusDays(1), today.minusDays(2))
        assertEquals(3, longestStreak(days))
    }

    @Test
    fun `longestStreak finds longest when there are multiple runs`() {
        val days =
            listOf(
                today,
                today.minusDays(1),
                today.minusDays(3),
                today.minusDays(4),
                today.minusDays(5),
                today.minusDays(6),
            )
        assertEquals(4, longestStreak(days))
    }

    @Test
    fun `longestStreak handles gap of more than one day`() {
        val days = listOf(today, today.minusDays(2), today.minusDays(3))
        assertEquals(2, longestStreak(days))
    }

    @Test
    fun `leadingStreak returns 1 for single day`() {
        assertEquals(1, leadingStreak(listOf(today)))
    }

    @Test
    fun `leadingStreak counts consecutive days from the front`() {
        val days = listOf(today, today.minusDays(1), today.minusDays(2), today.minusDays(4))
        assertEquals(3, leadingStreak(days))
    }

    @Test
    fun `leadingStreak stops at first gap`() {
        val days = listOf(today, today.minusDays(2), today.minusDays(3))
        assertEquals(1, leadingStreak(days))
    }
}
