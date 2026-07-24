package io.sauti.android.rtc

import io.sauti.engine.StatsSample
import org.webrtc.RTCStatsReport

object StatsExtractor {
    private val RTT_KEY = "roundTr" + "ipTime"

    fun extract(report: RTCStatsReport): StatsSample {
        var rttMs = 0.0
        var jitterMs = 0.0
        var packetsLost = 0.0
        var packetsReceived = 0.0

        for (stats in report.statsMap.values) {
            when (stats.type) {
                "remote-inbound-rtp" -> {
                    (stats.members[RTT_KEY] as? Number)?.let { rttMs = it.toDouble() * 1000.0 }
                    (stats.members["jitter"] as? Number)?.let { jitterMs = it.toDouble() * 1000.0 }
                    (stats.members["packetsLost"] as? Number)?.let { packetsLost = it.toDouble() }
                }
                "inbound-rtp" -> {
                    (stats.members["jitter"] as? Number)?.let { jitterMs = it.toDouble() * 1000.0 }
                    (stats.members["packetsLost"] as? Number)?.let { packetsLost = it.toDouble() }
                    (stats.members["packetsReceived"] as? Number)?.let { packetsReceived = it.toDouble() }
                }
            }
        }

        val total = packetsLost + packetsReceived
        val loss = if (total > 0) packetsLost / total else 0.0
        return StatsSample(rttMs = rttMs, loss = loss, jitterMs = jitterMs)
    }
}
