package com.satzwerk.sessions

import java.math.BigDecimal
import java.math.RoundingMode

internal const val EPLEY_DIVISOR = "30"

private const val EPLEY_DIVISION_SCALE = 10
private const val BRZYCKI_NUMERATOR = 36
private const val BRZYCKI_DENOMINATOR_BASE = 37
private const val ONE_RM_MAX_REPS = 37

/** Estimates one-rep max using the Epley formula: weight × (1 + reps / 30). Result is rounded to 2 decimal places.
 *  Returns null when reps <= 0 or reps >= 37 (formula produces meaningless results outside this range).
 */
fun epley(
    weight: BigDecimal,
    reps: Int,
): BigDecimal? {
    if (reps <= 0 || reps >= ONE_RM_MAX_REPS) return null
    return weight.multiply(
        BigDecimal.ONE.add(
            reps.toBigDecimal().divide(
                BigDecimal(EPLEY_DIVISOR),
                EPLEY_DIVISION_SCALE,
                RoundingMode.HALF_UP,
            ),
        ),
    ).setScale(2, RoundingMode.HALF_UP)
}

/** Estimates one-rep max using the Brzycki formula: weight × 36 / (37 − reps). Result is rounded to 2 decimal places.
 *  Returns null when reps <= 0 or reps >= 37 (division by zero at reps == 37; meaningless for reps > 37).
 */
internal fun brzycki(
    weight: BigDecimal,
    reps: Int,
): BigDecimal? {
    if (reps <= 0 || reps >= ONE_RM_MAX_REPS) return null
    val denominator = (BRZYCKI_DENOMINATOR_BASE - reps).toBigDecimal()
    return weight.multiply(BRZYCKI_NUMERATOR.toBigDecimal())
        .divide(denominator, EPLEY_DIVISION_SCALE, RoundingMode.HALF_UP)
        .setScale(2, RoundingMode.HALF_UP)
}
