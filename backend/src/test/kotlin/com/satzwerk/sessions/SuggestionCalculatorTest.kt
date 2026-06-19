package com.satzwerk.sessions

import com.satzwerk.workouts.WorkoutExercise
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID

private fun exercise(
    reps: Int,
    toFailure: Boolean = false,
    advancedTechnique: String? = null,
) = WorkoutExercise(
    id = UUID.randomUUID(),
    workoutGroupId = UUID.randomUUID(),
    exerciseId = UUID.randomUUID(),
    sets = 3,
    reps = reps,
    toFailure = toFailure,
    advancedTechnique = advancedTechnique,
)

class SuggestionCalculatorTest {
    private val oneRepMax = BigDecimal("116.67")

    @Test
    fun `returns null when workoutExercise is null`(): Unit =
        run {
            assertNull(computeSuggestedWeight(oneRepMax, null))
        }

    @Test
    fun `returns null when toFailure is true`(): Unit =
        run {
            assertNull(computeSuggestedWeight(oneRepMax, exercise(reps = 8, toFailure = true)))
        }

    @Test
    fun `uses Epley inverse for no technique`(): Unit =
        run {
            // 116.67 / (1 + 8/30) = 116.67 / 1.2666666667 = 92.11
            assertEquals(BigDecimal("92.11"), computeSuggestedWeight(oneRepMax, exercise(reps = 8)))
        }

    @Test
    fun `uses Epley inverse for SST`(): Unit =
        run {
            // 116.67 / (1 + 10/30) = 116.67 / 1.3333333333 = 87.50
            val result = computeSuggestedWeight(oneRepMax, exercise(reps = 10, advancedTechnique = "SST"))
            assertEquals(BigDecimal("87.50"), result)
        }

    @Test
    fun `uses Epley inverse for REST_PAUSE`(): Unit =
        run {
            // 116.67 / (1 + 8/30) = 92.11
            val result = computeSuggestedWeight(oneRepMax, exercise(reps = 8, advancedTechnique = "REST_PAUSE"))
            assertEquals(BigDecimal("92.11"), result)
        }

    @Test
    fun `uses 55 percent for GIRONDA`(): Unit =
        run {
            // 116.67 * 0.55 = 64.1685 -> 64.17
            val result = computeSuggestedWeight(oneRepMax, exercise(reps = 8, advancedTechnique = "GIRONDA"))
            assertEquals(BigDecimal("64.17"), result)
        }

    @Test
    fun `uses 65 percent for FST_7`(): Unit =
        run {
            // 116.67 * 0.65 = 75.8355 -> 75.84
            val result = computeSuggestedWeight(oneRepMax, exercise(reps = 12, advancedTechnique = "FST_7"))
            assertEquals(BigDecimal("75.84"), result)
        }

    @Test
    fun `uses 60 percent for GVT`(): Unit =
        run {
            // 116.67 * 0.60 = 70.002 -> 70.00
            val result = computeSuggestedWeight(oneRepMax, exercise(reps = 10, advancedTechnique = "GVT"))
            assertEquals(BigDecimal("70.00"), result)
        }
}
