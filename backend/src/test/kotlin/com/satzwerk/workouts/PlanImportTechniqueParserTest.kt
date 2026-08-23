package com.satzwerk.workouts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PlanImportTechniqueParserTest {
    private val parser = PlanImportTechniqueParser()

    @Test
    fun `parse maps known values fuzzily`() {
        assertEquals(AdvancedTechnique.REST_PAUSE.name, parser.parse("Rest Pause Cluster"))
        assertEquals(AdvancedTechnique.SST.name, parser.parse("Strip set"))
        assertEquals(AdvancedTechnique.GVT.name, parser.parse("GVT 10x10"))
        assertEquals(AdvancedTechnique.FST_7.name, parser.parse("FST-7 finisher"))
        assertEquals(AdvancedTechnique.GIRONDA.name, parser.parse("Gironda 8x8"))
    }

    @Test
    fun `parse returns null for unknown technique`() {
        assertNull(parser.parse("Unknown Technique"))
        assertNull(parser.parse(null))
    }
}
