package io.sauti.engine

data class QoeSummary(
    val samples: Int,
    val avgRttMs: Double,
    val maxRttMs: Double,
    val avgJitterMs: Double,
    val maxJitterMs: Double,
    val avgLoss: Double,
    val maxLoss: Double,
    val secGood: Double,
    val secDegraded: Double,
    val secPoor: Double,
    val worst: Quality
)

class QoeAggregator(private val intervalSec: Double) {
    private var samples = 0
    private var rttSum = 0.0
    private var rttCount = 0
    private var rttMax = 0.0
    private var jitterSum = 0.0
    private var jitterMax = 0.0
    private var lossSum = 0.0
    private var lossMax = 0.0
    private var secGood = 0.0
    private var secDegraded = 0.0
    private var secPoor = 0.0
    private var worst = Quality.GOOD

    @Synchronized
    fun observe(band: Quality, rttMs: Double, fractionLost: Double, jitterMs: Double) {
        samples += 1
        if (rttMs.isFinite() && rttMs > 0.0) {
            rttSum += rttMs
            rttCount += 1
            if (rttMs > rttMax) rttMax = rttMs
        }
        if (jitterMs.isFinite() && jitterMs >= 0.0) {
            jitterSum += jitterMs
            if (jitterMs > jitterMax) jitterMax = jitterMs
        }
        val loss = if (fractionLost.isFinite()) fractionLost else 0.0
        lossSum += loss
        if (loss > lossMax) lossMax = loss
        when (band) {
            Quality.POOR -> secPoor += intervalSec
            Quality.DEGRADED -> secDegraded += intervalSec
            else -> secGood += intervalSec
        }
        if (band.ordinal > worst.ordinal) worst = band
    }

    @Synchronized
    fun hasSamples(): Boolean = samples > 0

    @Synchronized
    fun summary(): QoeSummary? {
        if (samples == 0) return null
        return QoeSummary(
            samples = samples,
            avgRttMs = round(clamp(if (rttCount > 0) rttSum / rttCount else 0.0, 0.0, 5000.0), 1),
            maxRttMs = round(clamp(rttMax, 0.0, 5000.0), 1),
            avgJitterMs = round(clamp(jitterSum / samples, 0.0, 2000.0), 1),
            maxJitterMs = round(clamp(jitterMax, 0.0, 2000.0), 1),
            avgLoss = round(clamp(lossSum / samples, 0.0, 1.0), 4),
            maxLoss = round(clamp(lossMax, 0.0, 1.0), 4),
            secGood = round(clamp(secGood, 0.0, 86400.0), 1),
            secDegraded = round(clamp(secDegraded, 0.0, 86400.0), 1),
            secPoor = round(clamp(secPoor, 0.0, 86400.0), 1),
            worst = worst
        )
    }

    private fun clamp(value: Double, lo: Double, hi: Double): Double {
        if (!value.isFinite()) return lo
        return minOf(hi, maxOf(lo, value))
    }

    private fun round(value: Double, places: Int): Double {
        val factor = Math.pow(10.0, places.toDouble())
        return Math.round(value * factor) / factor
    }
}
