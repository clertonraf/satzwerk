package com.satzwerk.sessions

import com.satzwerk.workouts.AdvancedTechnique
import com.satzwerk.workouts.WorkoutExercise
import java.math.BigDecimal
import java.math.RoundingMode

private const val SUGGESTION_REPS_SCALE = 10
private const val GIRONDA_PERCENT = "0.55"
private const val FST_7_PERCENT = "0.65"
private const val GVT_PERCENT = "0.60"

internal fun computeSuggestedWeight(
    oneRepMaxKg: BigDecimal,
    workoutExercise: WorkoutExercise?,
): BigDecimal? {
    if (workoutExercise == null || workoutExercise.toFailure) return null

    val technique = workoutExercise.advancedTechnique?.let { AdvancedTechnique.valueOf(it) }

    return when (technique) {
        AdvancedTechnique.GIRONDA ->
            oneRepMaxKg.multiply(BigDecimal(GIRONDA_PERCENT)).setScale(2, RoundingMode.HALF_UP)

        AdvancedTechnique.FST_7 ->
            oneRepMaxKg.multiply(BigDecimal(FST_7_PERCENT)).setScale(2, RoundingMode.HALF_UP)

        AdvancedTechnique.GVT ->
            oneRepMaxKg.multiply(BigDecimal(GVT_PERCENT)).setScale(2, RoundingMode.HALF_UP)

        null, AdvancedTechnique.SST, AdvancedTechnique.REST_PAUSE -> {
            val ratio =
                workoutExercise.reps.toBigDecimal()
                    .divide(BigDecimal(EPLEY_DIVISOR), SUGGESTION_REPS_SCALE, RoundingMode.HALF_UP)
            val divisor = BigDecimal.ONE.add(ratio)
            oneRepMaxKg.divide(divisor, 2, RoundingMode.HALF_UP)
        }
    }
}
