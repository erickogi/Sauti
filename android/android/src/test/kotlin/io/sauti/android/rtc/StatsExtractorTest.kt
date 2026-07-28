package io.sauti.android.rtc

import org.webrtc.RTCStats
import org.webrtc.RTCStatsReport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private fun stat(type: String, members: Map<String, Any>): RTCStats =
    RTCStats(0L, type, type, members)

private fun report(vararg stats: RTCStats): RTCStatsReport =
    RTCStatsReport(0L, stats.associateBy { it.id })

class StatsExtractorTest {
    @Test
    fun coreFieldsMatchArithmeticAndExtrasAreNullWhenAbsent() {
        val sample = StatsExtractor.extract(
            report(
                stat(
                    "remote-inbound-rtp",
                    mapOf("roundTripTime" to 0.2, "jitter" to 0.01, "packetsLost" to 5)
                ),
                stat(
                    "inbound-rtp",
                    mapOf("jitter" to 0.02, "packetsLost" to 5, "packetsReceived" to 95)
                )
            )
        )

        assertEquals(200.0, sample.rttMs)
        assertEquals(20.0, sample.jitterMs)
        assertEquals(0.05, sample.loss)
        assertNull(sample.audioLevel)
        assertNull(sample.jitterBufferMs)
        assertNull(sample.fractionLost)
    }

    @Test
    fun extrasFilledWhenPresentInSameReport() {
        val sample = StatsExtractor.extract(
            report(
                stat(
                    "remote-inbound-rtp",
                    mapOf("roundTripTime" to 0.1, "jitter" to 0.01, "packetsLost" to 2, "fractionLost" to 0.03)
                ),
                stat(
                    "inbound-rtp",
                    mapOf(
                        "jitter" to 0.02,
                        "packetsLost" to 2,
                        "packetsReceived" to 98,
                        "audioLevel" to 0.6,
                        "jitterBufferDelay" to 4.0,
                        "jitterBufferEmittedCount" to 100
                    )
                )
            )
        )

        assertEquals(100.0, sample.rttMs)
        assertEquals(20.0, sample.jitterMs)
        assertEquals(0.02, sample.loss)
        assertEquals(0.6, sample.audioLevel)
        assertEquals(40.0, sample.jitterBufferMs)
        assertEquals(0.03, sample.fractionLost)
    }

    @Test
    fun jitterBufferMsIsNullWhenEmittedCountIsZero() {
        val sample = StatsExtractor.extract(
            report(
                stat(
                    "inbound-rtp",
                    mapOf(
                        "jitter" to 0.02,
                        "packetsReceived" to 100,
                        "jitterBufferDelay" to 4.0,
                        "jitterBufferEmittedCount" to 0
                    )
                )
            )
        )

        assertNull(sample.jitterBufferMs)
    }

    @Test
    fun lossIsZeroWhenNoPackets() {
        val sample = StatsExtractor.extract(report())

        assertEquals(0.0, sample.rttMs)
        assertEquals(0.0, sample.jitterMs)
        assertEquals(0.0, sample.loss)
        assertNull(sample.audioLevel)
        assertNull(sample.jitterBufferMs)
        assertNull(sample.fractionLost)
    }
}
