package io.sauti.engine

import kotlin.test.Test
import kotlin.test.assertEquals

class QualityDtxTest {

    private fun silenceSample(loss: Double): StatsSample =
        StatsSample(rttMs = 20.0, loss = loss, jitterMs = 10.0)

    @Test
    fun risingLossDuringSilenceWalksGoodToPoorWithFallback() {
        val tracker = QualityTracker()
        val losses = listOf(0.0, 0.02, 0.05, 0.08, 0.12, 0.15, 0.2)

        val observed = losses.map { tracker.observe(silenceSample(it)) }

        val expected = listOf(
            QualityObservation(Quality.GOOD, false),
            QualityObservation(Quality.GOOD, false),
            QualityObservation(Quality.GOOD, false),
            QualityObservation(Quality.DEGRADED, false),
            QualityObservation(Quality.DEGRADED, false),
            QualityObservation(Quality.POOR, true),
            QualityObservation(Quality.POOR, true)
        )

        assertEquals(expected, observed)
    }

    @Test
    fun risingLossClassifierLabelsAreStable() {
        assertEquals(Quality.GOOD, classifyQuality(silenceSample(0.02)))
        assertEquals(Quality.DEGRADED, classifyQuality(silenceSample(0.05)))
        assertEquals(Quality.POOR, classifyQuality(silenceSample(0.12)))
    }
}
