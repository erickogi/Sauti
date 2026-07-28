package io.sauti.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OpusSdpTest {

    private fun join(lines: List<String>, eol: String = "\n"): String = lines.joinToString(eol)

    private val baseWithFmtp = listOf(
        "v=0",
        "o=- 1 2 IN IP4 127.0.0.1",
        "s=-",
        "t=0 0",
        "m=audio 9 UDP/TLS/RTP/SAVPF 111",
        "a=rtpmap:111 opus/48000/2",
        "a=fmtp:111 minptime=10"
    )

    @Test
    fun bothFlagsFalseReturnsInputUnchanged() {
        val lf = join(baseWithFmtp)
        val crlf = join(baseWithFmtp, "\r\n")
        assertEquals(lf, transformOpusSdp(lf, dtx = false, fec = false))
        assertEquals(crlf, transformOpusSdp(crlf, dtx = false, fec = false))
    }

    @Test
    fun bothFlagsFalseIsIdentityForOddInputs() {
        assertEquals("", transformOpusSdp("", dtx = false, fec = false))
        assertEquals("not an sdp", transformOpusSdp("not an sdp", dtx = false, fec = false))
        assertEquals("m=audio\n", transformOpusSdp("m=audio\n", dtx = false, fec = false))
    }

    @Test
    fun fecMergesIntoExistingFmtpPreservingOtherKeys() {
        val out = transformOpusSdp(join(baseWithFmtp), dtx = false, fec = true)
        assertEquals(
            join(
                listOf(
                    "v=0",
                    "o=- 1 2 IN IP4 127.0.0.1",
                    "s=-",
                    "t=0 0",
                    "m=audio 9 UDP/TLS/RTP/SAVPF 111",
                    "a=rtpmap:111 opus/48000/2",
                    "a=fmtp:111 minptime=10;useinbandfec=1"
                )
            ),
            out
        )
    }

    @Test
    fun fecReplacesExistingValueWithoutDuplicating() {
        val input = join(
            listOf(
                "m=audio 9 UDP/TLS/RTP/SAVPF 111",
                "a=rtpmap:111 opus/48000/2",
                "a=fmtp:111 minptime=10;useinbandfec=0"
            )
        )
        val out = transformOpusSdp(input, dtx = false, fec = true)
        assertEquals(
            join(
                listOf(
                    "m=audio 9 UDP/TLS/RTP/SAVPF 111",
                    "a=rtpmap:111 opus/48000/2",
                    "a=fmtp:111 minptime=10;useinbandfec=1"
                )
            ),
            out
        )
    }

    @Test
    fun fecInsertsFmtpWhenAbsentDirectlyAfterRtpmap() {
        val input = join(
            listOf(
                "m=audio 9 UDP/TLS/RTP/SAVPF 111",
                "a=rtpmap:111 opus/48000/2",
                "a=rtcp-fb:111 transport-cc"
            )
        )
        val out = transformOpusSdp(input, dtx = false, fec = true)
        assertEquals(
            join(
                listOf(
                    "m=audio 9 UDP/TLS/RTP/SAVPF 111",
                    "a=rtpmap:111 opus/48000/2",
                    "a=fmtp:111 useinbandfec=1",
                    "a=rtcp-fb:111 transport-cc"
                )
            ),
            out
        )
    }

    @Test
    fun fmtpWithoutParamsGainsOnlyTheTokens() {
        val input = join(
            listOf(
                "m=audio 9 UDP/TLS/RTP/SAVPF 111",
                "a=rtpmap:111 opus/48000/2",
                "a=fmtp:111"
            )
        )
        val out = transformOpusSdp(input, dtx = false, fec = true)
        assertEquals(
            join(
                listOf(
                    "m=audio 9 UDP/TLS/RTP/SAVPF 111",
                    "a=rtpmap:111 opus/48000/2",
                    "a=fmtp:111 useinbandfec=1"
                )
            ),
            out
        )
    }

    @Test
    fun onlyOpusPayloadIsEditedWhenPtIsNot111() {
        val input = join(
            listOf(
                "m=audio 9 UDP/TLS/RTP/SAVPF 63 0",
                "a=rtpmap:63 opus/48000/2",
                "a=fmtp:63 minptime=10",
                "a=rtpmap:0 PCMU/8000",
                "a=fmtp:0 something=1"
            )
        )
        val out = transformOpusSdp(input, dtx = false, fec = true)
        assertEquals(
            join(
                listOf(
                    "m=audio 9 UDP/TLS/RTP/SAVPF 63 0",
                    "a=rtpmap:63 opus/48000/2",
                    "a=fmtp:63 minptime=10;useinbandfec=1",
                    "a=rtpmap:0 PCMU/8000",
                    "a=fmtp:0 something=1"
                )
            ),
            out
        )
    }

    @Test
    fun sectionWithNoOpusRtpmapIsLeftUnchanged() {
        val input = join(
            listOf(
                "m=audio 9 UDP/TLS/RTP/SAVPF 0 8",
                "a=rtpmap:0 PCMU/8000",
                "a=rtpmap:8 PCMA/8000"
            )
        )
        assertEquals(input, transformOpusSdp(input, dtx = false, fec = true))
    }

    @Test
    fun videoSectionsAndPreambleAreNeverTouched() {
        val input = join(
            listOf(
                "v=0",
                "m=video 9 UDP/TLS/RTP/SAVPF 96",
                "a=rtpmap:96 VP8/90000",
                "a=fmtp:96 max-fr=30",
                "m=audio 9 UDP/TLS/RTP/SAVPF 111",
                "a=rtpmap:111 opus/48000/2",
                "a=fmtp:111 minptime=10"
            )
        )
        val out = transformOpusSdp(input, dtx = false, fec = true)
        assertEquals(
            join(
                listOf(
                    "v=0",
                    "m=video 9 UDP/TLS/RTP/SAVPF 96",
                    "a=rtpmap:96 VP8/90000",
                    "a=fmtp:96 max-fr=30",
                    "m=audio 9 UDP/TLS/RTP/SAVPF 111",
                    "a=rtpmap:111 opus/48000/2",
                    "a=fmtp:111 minptime=10;useinbandfec=1"
                )
            ),
            out
        )
    }

    @Test
    fun twoAudioSectionsAreHandledIndependentlyWithPerSectionPt() {
        val input = join(
            listOf(
                "m=audio 9 UDP/TLS/RTP/SAVPF 111",
                "a=rtpmap:111 opus/48000/2",
                "a=fmtp:111 minptime=10",
                "m=audio 9 UDP/TLS/RTP/SAVPF 96",
                "a=rtpmap:96 opus/48000/2",
                "a=rtcp-fb:96 transport-cc"
            )
        )
        val out = transformOpusSdp(input, dtx = false, fec = true)
        assertEquals(
            join(
                listOf(
                    "m=audio 9 UDP/TLS/RTP/SAVPF 111",
                    "a=rtpmap:111 opus/48000/2",
                    "a=fmtp:111 minptime=10;useinbandfec=1",
                    "m=audio 9 UDP/TLS/RTP/SAVPF 96",
                    "a=rtpmap:96 opus/48000/2",
                    "a=fmtp:96 useinbandfec=1",
                    "a=rtcp-fb:96 transport-cc"
                )
            ),
            out
        )
    }

    @Test
    fun payloadTypeIsRederivedPerCallAndNeverCached() {
        val at111 = join(
            listOf(
                "m=audio 9 UDP/TLS/RTP/SAVPF 111",
                "a=rtpmap:111 opus/48000/2",
                "a=fmtp:111 minptime=10"
            )
        )
        val at96 = join(
            listOf(
                "m=audio 9 UDP/TLS/RTP/SAVPF 96",
                "a=rtpmap:96 opus/48000/2",
                "a=fmtp:96 minptime=10"
            )
        )
        val out111 = transformOpusSdp(at111, dtx = false, fec = true)
        val out96 = transformOpusSdp(at96, dtx = false, fec = true)
        assertTrue(out111.contains("a=fmtp:111 minptime=10;useinbandfec=1"))
        assertFalse(out111.contains("a=fmtp:96"))
        assertTrue(out96.contains("a=fmtp:96 minptime=10;useinbandfec=1"))
        assertFalse(out96.contains("a=fmtp:111 minptime=10;useinbandfec=1"))
    }

    @Test
    fun transformIsIdempotent() {
        val once = transformOpusSdp(join(baseWithFmtp), dtx = false, fec = true)
        val twice = transformOpusSdp(once, dtx = false, fec = true)
        assertEquals(once, twice)
        assertEquals(1, Regex("useinbandfec=1").findAll(twice).count())
    }

    @Test
    fun idempotentWhenTokenAlreadyPresent() {
        val input = join(
            listOf(
                "m=audio 9 UDP/TLS/RTP/SAVPF 111",
                "a=rtpmap:111 opus/48000/2",
                "a=fmtp:111 useinbandfec=1"
            )
        )
        assertEquals(input, transformOpusSdp(input, dtx = false, fec = true))
    }

    @Test
    fun offerAndAnswerAreMungedByTheSameTransform() {
        val offer = join(
            listOf(
                "m=audio 9 UDP/TLS/RTP/SAVPF 111",
                "a=rtpmap:111 opus/48000/2",
                "a=fmtp:111 minptime=10"
            )
        )
        val answer = join(
            listOf(
                "m=audio 9 UDP/TLS/RTP/SAVPF 111",
                "a=rtpmap:111 opus/48000/2",
                "a=rtcp-fb:111 transport-cc"
            )
        )
        assertTrue(transformOpusSdp(offer, dtx = false, fec = true).contains("useinbandfec=1"))
        assertTrue(transformOpusSdp(answer, dtx = false, fec = true).contains("a=fmtp:111 useinbandfec=1"))
    }

    @Test
    fun crlfLineEndingsArePreserved() {
        val input = join(baseWithFmtp, "\r\n")
        val out = transformOpusSdp(input, dtx = false, fec = true)
        assertEquals(
            join(
                listOf(
                    "v=0",
                    "o=- 1 2 IN IP4 127.0.0.1",
                    "s=-",
                    "t=0 0",
                    "m=audio 9 UDP/TLS/RTP/SAVPF 111",
                    "a=rtpmap:111 opus/48000/2",
                    "a=fmtp:111 minptime=10;useinbandfec=1"
                ),
                "\r\n"
            ),
            out
        )
        assertTrue(out.contains("\r\n"))
    }

    @Test
    fun lfLineEndingsStayLf() {
        val out = transformOpusSdp(join(baseWithFmtp), dtx = false, fec = true)
        assertFalse(out.contains("\r"))
    }

    @Test
    fun crlfInsertionUsesCrlfForTheNewLine() {
        val input = join(
            listOf(
                "m=audio 9 UDP/TLS/RTP/SAVPF 111",
                "a=rtpmap:111 opus/48000/2",
                "a=rtcp-fb:111 transport-cc"
            ),
            "\r\n"
        )
        val out = transformOpusSdp(input, dtx = false, fec = true)
        assertTrue(out.contains("a=fmtp:111 useinbandfec=1\r\n"))
    }

    @Test
    fun dtxOnlyInsertsUsedtx() {
        val input = join(
            listOf(
                "m=audio 9 UDP/TLS/RTP/SAVPF 111",
                "a=rtpmap:111 opus/48000/2",
                "a=fmtp:111 minptime=10"
            )
        )
        val out = transformOpusSdp(input, dtx = true, fec = false)
        assertTrue(out.contains("a=fmtp:111 minptime=10;usedtx=1"))
        assertFalse(out.contains("useinbandfec"))
    }

    @Test
    fun dtxAndFecTogetherLandBothTokensOnOneFmtp() {
        val input = join(
            listOf(
                "m=audio 9 UDP/TLS/RTP/SAVPF 111",
                "a=rtpmap:111 opus/48000/2",
                "a=rtcp-fb:111 transport-cc"
            )
        )
        val out = transformOpusSdp(input, dtx = true, fec = true)
        assertTrue(out.contains("a=fmtp:111 useinbandfec=1;usedtx=1"))
    }

    @Test
    fun dtxAndFecMergeIntoExistingFmtpInOrder() {
        val out = transformOpusSdp(join(baseWithFmtp), dtx = true, fec = true)
        assertTrue(out.contains("a=fmtp:111 minptime=10;useinbandfec=1;usedtx=1"))
    }

    @Test
    fun dtxAndFecAreIdempotent() {
        val once = transformOpusSdp(join(baseWithFmtp), dtx = true, fec = true)
        val twice = transformOpusSdp(once, dtx = true, fec = true)
        assertEquals(once, twice)
    }

    @Test
    fun opusRtpmapMatchIsCaseSensitiveAndKeepsChannels() {
        val input = join(
            listOf(
                "m=audio 9 UDP/TLS/RTP/SAVPF 111",
                "a=rtpmap:111 opus/48000/2",
                "a=fmtp:111 minptime=10"
            )
        )
        val out = transformOpusSdp(input, dtx = false, fec = true)
        assertTrue(out.contains("a=fmtp:111 minptime=10;useinbandfec=1"))
    }

    @Test
    fun uppercaseOpusRtpmapIsNotMatched() {
        val input = join(
            listOf(
                "m=audio 9 UDP/TLS/RTP/SAVPF 111",
                "a=rtpmap:111 OPUS/48000/2",
                "a=fmtp:111 minptime=10"
            )
        )
        assertEquals(input, transformOpusSdp(input, dtx = false, fec = true))
    }

    @Test
    fun crlfFinalRtpmapWithoutTerminatorInsertsFmtpWithCrlf() {
        val input = "m=audio 9 UDP/TLS/RTP/SAVPF 111\r\na=rtpmap:111 opus/48000/2"
        val out = transformOpusSdp(input, dtx = false, fec = true)
        assertEquals(
            "m=audio 9 UDP/TLS/RTP/SAVPF 111\r\na=rtpmap:111 opus/48000/2\r\na=fmtp:111 useinbandfec=1",
            out
        )
    }

    @Test
    fun bareValuelessTokenIsReplacedNotDuplicated() {
        val input = join(
            listOf(
                "m=audio 9 UDP/TLS/RTP/SAVPF 111",
                "a=rtpmap:111 opus/48000/2",
                "a=fmtp:111 minptime=10;useinbandfec"
            )
        )
        val out = transformOpusSdp(input, dtx = false, fec = true)
        assertEquals(
            join(
                listOf(
                    "m=audio 9 UDP/TLS/RTP/SAVPF 111",
                    "a=rtpmap:111 opus/48000/2",
                    "a=fmtp:111 minptime=10;useinbandfec=1"
                )
            ),
            out
        )
    }

    @Test
    fun valuelessTokenIsPreservedWhileMerging() {
        val input = join(
            listOf(
                "m=audio 9 UDP/TLS/RTP/SAVPF 111",
                "a=rtpmap:111 opus/48000/2",
                "a=fmtp:111 minptime=10;cbr"
            )
        )
        val out = transformOpusSdp(input, dtx = false, fec = true)
        assertEquals(
            join(
                listOf(
                    "m=audio 9 UDP/TLS/RTP/SAVPF 111",
                    "a=rtpmap:111 opus/48000/2",
                    "a=fmtp:111 minptime=10;cbr;useinbandfec=1"
                )
            ),
            out
        )
    }

    @Test
    fun overlongClockRateRtpmapIsNotMatched() {
        val input = join(
            listOf(
                "m=audio 9 UDP/TLS/RTP/SAVPF 111",
                "a=rtpmap:111 opus/480000/2",
                "a=fmtp:111 minptime=10"
            )
        )
        assertEquals(input, transformOpusSdp(input, dtx = false, fec = true))
    }

    @Test
    fun tabSeparatedRtpmapIsNotMatched() {
        val input = join(
            listOf(
                "m=audio 9 UDP/TLS/RTP/SAVPF 111",
                "a=rtpmap:111\topus/48000/2",
                "a=fmtp:111 minptime=10"
            )
        )
        assertEquals(input, transformOpusSdp(input, dtx = false, fec = true))
    }

    @Test
    fun trailingJunkRtpmapIsNotMatched() {
        val input = join(
            listOf(
                "m=audio 9 UDP/TLS/RTP/SAVPF 111",
                "a=rtpmap:111 opus/48000/2 extra",
                "a=fmtp:111 minptime=10"
            )
        )
        assertEquals(input, transformOpusSdp(input, dtx = false, fec = true))
    }

    @Test
    fun defaultOpusConfigCarriesBothFlagsFalse() {
        val config = OpusConfig()
        assertFalse(config.fec)
        assertFalse(config.dtx)
    }
}
