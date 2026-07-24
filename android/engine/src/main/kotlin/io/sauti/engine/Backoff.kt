package io.sauti.engine

import kotlin.math.min
import kotlin.math.roundToLong

fun computeBackoff(
    attempt: Int,
    baseMs: Long,
    maxMs: Long,
    random: () -> Double
): Long {
    val exponential = baseMs.toDouble() * Math.pow(2.0, attempt.toDouble())
    val capped = min(exponential, maxMs.toDouble())
    val jitter = capped * 0.25 * random()
    return (capped - capped * 0.25 + jitter).roundToLong()
}
