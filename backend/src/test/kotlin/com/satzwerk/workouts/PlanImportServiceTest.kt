package com.satzwerk.workouts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

class PlanImportServiceTest {
    private val service =
        PlanImportService(
            planParser = SatzwerkParserClient("http://localhost"),
            workoutPlanRepository = mock(WorkoutPlanRepository::class.java),
            workoutGroupRepository = mock(WorkoutGroupRepository::class.java),
            workoutExerciseRepository = mock(WorkoutExerciseRepository::class.java),
            exerciseRepository = mock(ExerciseRepository::class.java),
        )

    @Test
    fun `mapTechnique maps known values fuzzily`() {
        assertEquals(AdvancedTechnique.REST_PAUSE.name, service.mapTechnique("Rest Pause Cluster"))
        assertEquals(AdvancedTechnique.SST.name, service.mapTechnique("Strip set"))
        assertEquals(AdvancedTechnique.GVT.name, service.mapTechnique("GVT 10x10"))
        assertEquals(AdvancedTechnique.FST_7.name, service.mapTechnique("FST-7 finisher"))
        assertEquals(AdvancedTechnique.GIRONDA.name, service.mapTechnique("Gironda 8x8"))
    }

    @Test
    fun `mapTechnique returns null for unknown technique`() {
        assertNull(service.mapTechnique("Unknown Technique"))
        assertNull(service.mapTechnique(null))
    }

    @Test
    fun `planNameFromFilename strips extension and normalizes separators`() {
        assertEquals("Push Pull Legs", service.planNameFromFilename("Push_Pull-Legs.xlsx"))
    }

    @Test
    fun `planNameFromFilename keeps filename when no extension exists`() {
        assertEquals("Treino A", service.planNameFromFilename("Treino A"))
    }
}
