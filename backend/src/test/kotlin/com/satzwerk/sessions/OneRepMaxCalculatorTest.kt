package com.satzwerk.sessions

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class OneRepMaxCalculatorTest {
    @Test
    fun `epley returns expected 1RM for standard 5-rep set`(): Unit =
        run {
            // 100 * (1 + 5/30) = 100 * 1.1666666667 = 116.67
            assertEquals(BigDecimal("116.67"), epley(BigDecimal("100"), 5))
        }

    @Test
    fun `epley returns null for 0 reps`(): Unit =
        run {
            assertNull(epley(BigDecimal("100"), 0))
        }

    @Test
    fun `epley returns null for negative reps`(): Unit =
        run {
            assertNull(epley(BigDecimal("100"), -1))
        }

    @Test
    fun `epley returns null for reps at boundary 37`(): Unit =
        run {
            assertNull(epley(BigDecimal("100"), 37))
        }

    @Test
    fun `epley returns null for reps above 37`(): Unit =
        run {
            assertNull(epley(BigDecimal("100"), 38))
        }

    @Test
    fun `epley scales correctly for heavy low-rep set`(): Unit =
        run {
            // 120 * (1 + 1/30) = 120 * 1.0333333333 = 124.00
            assertEquals(BigDecimal("124.00"), epley(BigDecimal("120"), 1))
        }

    @Test
    fun `epley scales correctly for high-rep set`(): Unit =
        run {
            // 80 * (1 + 12/30) = 80 * 1.4 = 112.00
            assertEquals(BigDecimal("112.00"), epley(BigDecimal("80"), 12))
        }

    @Test
    fun `brzycki returns expected 1RM for standard 5-rep set`(): Unit =
        run {
            // 100 * 36 / (37 - 5) = 100 * 36 / 32 = 112.50
            assertEquals(BigDecimal("112.50"), brzycki(BigDecimal("100"), 5))
        }

    @Test
    fun `brzycki scales correctly for heavy low-rep set`(): Unit =
        run {
            // 120 * 36 / (37 - 1) = 120 * 36 / 36 = 120.00
            assertEquals(BigDecimal("120.00"), brzycki(BigDecimal("120"), 1))
        }

    @Test
    fun `brzycki returns expected 1RM for 12-rep set`(): Unit =
        run {
            // 80 * 36 / (37 - 12) = 80 * 36 / 25 = 115.20
            assertEquals(BigDecimal("115.20"), brzycki(BigDecimal("80"), 12))
        }

    @Test
    fun `brzycki returns null for 0 reps`(): Unit =
        run {
            assertNull(brzycki(BigDecimal("100"), 0))
        }

    @Test
    fun `brzycki returns null for negative reps`(): Unit =
        run {
            assertNull(brzycki(BigDecimal("100"), -1))
        }

    @Test
    fun `brzycki returns null for reps at boundary 37`(): Unit =
        run {
            assertNull(brzycki(BigDecimal("100"), 37))
        }

    @Test
    fun `brzycki returns null for reps above 37`(): Unit =
        run {
            assertNull(brzycki(BigDecimal("100"), 38))
        }

    @Test
    fun `brzycki returns null for reps at 36 is valid`(): Unit =
        run {
            // 100 * 36 / (37 - 36) = 100 * 36 / 1 = 3600.00
            assertEquals(BigDecimal("3600.00"), brzycki(BigDecimal("100"), 36))
        }
}
