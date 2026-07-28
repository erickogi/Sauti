package io.sauti.android.rtc

import io.sauti.engine.OpusConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebRtcConnectionOpusTest {

    private val offerSdp = listOf(
        "v=0",
        "m=audio 9 UDP/TLS/RTP/SAVPF 111",
        "a=rtpmap:111 opus/48000/2",
        "a=fmtp:111 minptime=10"
    ).joinToString("\n")

    private val answerSdp = listOf(
        "v=0",
        "m=audio 9 UDP/TLS/RTP/SAVPF 111",
        "a=rtpmap:111 opus/48000/2",
        "a=rtcp-fb:111 transport-cc"
    ).joinToString("\n")

    @Test
    fun defaultConnectionLeavesCreatedSdpByteIdentical() {
        val connection = WebRtcConnection("sauti-stream")
        assertEquals(offerSdp, connection.applyOpus(offerSdp))
        assertEquals(answerSdp, connection.applyOpus(answerSdp))
    }

    @Test
    fun fecConfigMungesBothCreatedOfferAndAnswerSymmetrically() {
        val connection = WebRtcConnection("sauti-stream", OpusConfig(fec = true))
        assertTrue(connection.applyOpus(offerSdp).contains("a=fmtp:111 minptime=10;useinbandfec=1"))
        assertTrue(connection.applyOpus(answerSdp).contains("a=fmtp:111 useinbandfec=1"))
    }

    @Test
    fun fecConfigNeverEmitsDtxBecauseTwoBWiresDtxFalse() {
        val connection = WebRtcConnection("sauti-stream", OpusConfig(fec = true, dtx = true))
        assertFalse(connection.applyOpus(offerSdp).contains("usedtx"))
    }

    @Test
    fun fecMungeIsIdempotent() {
        val connection = WebRtcConnection("sauti-stream", OpusConfig(fec = true))
        val once = connection.applyOpus(offerSdp)
        assertEquals(once, connection.applyOpus(once))
    }
}
