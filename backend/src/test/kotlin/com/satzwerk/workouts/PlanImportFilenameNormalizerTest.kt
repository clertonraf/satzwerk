package com.satzwerk.workouts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PlanImportFilenameNormalizerTest {
    private val normalizer = PlanImportFilenameNormalizer()

    @Test
    fun `normalize strips extension and normalizes separators`() {
        assertEquals("Push Pull Legs", normalizer.normalize("Push_Pull-Legs.xlsx"))
    }

    @Test
    fun `normalize keeps filename when no extension exists`() {
        assertEquals("Treino A", normalizer.normalize("Treino A"))
    }
}
