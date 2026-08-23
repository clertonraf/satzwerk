package com.satzwerk.medications

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class FrequencySpecTest {
    private val frequencySpecModule = FrequencySpecModule(ObjectMapper().findAndRegisterModules())

    @Test
    fun `daily scheduledCountOn returns times per day`() {
        val spec = FrequencySpec.Daily(timesPerDay = 3)

        assertEquals(3, spec.scheduledCountOn(LocalDate.of(2026, 8, 23)))
    }

    @Test
    fun `weekly isDueOn returns false for non matching weekday`() {
        val spec = FrequencySpec.Weekly(timesPerWeek = 2, weekdays = listOf(1, 3))

        assertFalse(spec.isDueOn(LocalDate.of(2026, 8, 23)))
    }

    @Test
    fun `monthly scheduledCountOn clamps days beyond month length to final day`() {
        val spec = FrequencySpec.Monthly(timesPerMonth = 1, daysOfMonth = listOf(31))

        assertEquals(1, spec.scheduledCountOn(LocalDate.of(2026, 2, 28)))
        assertTrue(spec.isDueOn(LocalDate.of(2026, 2, 28)))
    }

    @Test
    fun `module round trips JSON without changing representation`() {
        val spec = FrequencySpec.Monthly(timesPerMonth = 2, daysOfMonth = listOf(5, 31))

        val restored = frequencySpecModule.deserialize(frequencySpecModule.serialize(spec))

        assertEquals(spec, restored)
    }
}
