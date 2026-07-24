package io.sauti.engine

private const val HYSTERESIS = 2

fun classifyQuality(sample: StatsSample): Quality {
    if (sample.loss > 0.1 || sample.rttMs > 400 || sample.jitterMs > 100) {
        return Quality.POOR
    }
    if (sample.loss > 0.03 || sample.rttMs > 200 || sample.jitterMs > 50) {
        return Quality.DEGRADED
    }
    return Quality.GOOD
}

data class QualityObservation(val label: Quality, val fallback: Boolean)

class QualityTracker {
    private var label: Quality = Quality.GOOD
    private var pending: Quality = Quality.GOOD
    private var pendingCount = 0
    private var consecutivePoor = 0

    fun observe(sample: StatsSample): QualityObservation {
        val raw = classifyQuality(sample)

        if (raw == Quality.POOR) consecutivePoor += 1 else consecutivePoor = 0

        when {
            raw == label -> {
                pending = raw
                pendingCount = 0
            }
            raw == pending -> {
                pendingCount += 1
                if (pendingCount >= HYSTERESIS) {
                    label = raw
                    pendingCount = 0
                }
            }
            else -> {
                pending = raw
                pendingCount = 1
            }
        }

        return QualityObservation(label, consecutivePoor >= 2)
    }
}

private val QUALITY_RANK = mapOf(
    Quality.GOOD to 0,
    Quality.DEGRADED to 1,
    Quality.POOR to 2
)

fun worstQuality(a: Quality, b: Quality): Quality =
    if (QUALITY_RANK.getValue(a) >= QUALITY_RANK.getValue(b)) a else b
