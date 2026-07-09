package com.satzwerk.sessions

import java.math.BigDecimal
import java.math.RoundingMode

internal const val EPLEY_DIVISOR = "30"

private const val EPLEY_DIVISION_SCALE = 10

/** Estimates one-rep max using the Epley formula: weight × (1 + reps / 30). Result is rounded to 2 decimal places. */
internal fun epley(
    weight: BigDecimal,
    reps: Int,
): BigDecimal =
    weight.multiply(
        BigDecimal.ONE.add(
            reps.toBigDecimal().divide(
                BigDecimal(EPLEY_DIVISOR),
                EPLEY_DIVISION_SCALE,
                RoundingMode.HALF_UP,
            ),
        ),
    ).setScale(2, RoundingMode.HALF_UP)
