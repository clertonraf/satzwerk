package com.satzwerk.sessions

import org.junit.jupiter.api.Assertions.assertEquals
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
    fun `epley returns weight unchanged for 0 reps`(): Unit =
        run {
            // 100 * (1 + 0/30) = 100 * 1.0 = 100.00
            assertEquals(BigDecimal("100.00"), epley(BigDecimal("100"), 0))
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
}
