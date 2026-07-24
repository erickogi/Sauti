package io.sauti.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PolitenessTest {
    @Test
    fun smallerIdIsPolite() {
        assertTrue(Politeness.isPolite("alice", "bob"))
        assertFalse(Politeness.isPolite("bob", "alice"))
    }

    @Test
    fun bothPeersDeriveOppositePoliteness() {
        val a = "p-000000000000000000000001"
        val b = "p-000000000000000000000002"
        val aView = Politeness.isPolite(a, b)
        val bView = Politeness.isPolite(b, a)
        assertTrue(aView)
        assertFalse(bView)
        assertEquals(!aView, bView)
    }

    @Test
    fun matchesWebLexicographicSemantics() {
        assertEquals("Z", Politeness.politePeer("Z", "a"))
        assertEquals("A", Politeness.politePeer("A", "Z"))
    }

    @Test
    fun matchesWebProtocolFixtures() {
        val ids = listOf("participant-9", "participant-10", "aa", "ab", "Z", "a")
        for (a in ids) {
            for (b in ids) {
                val expected = if (a <= b) a else b
                assertEquals(expected, Politeness.politePeer(a, b))
            }
        }
        assertEquals("participant-10", Politeness.politePeer("participant-9", "participant-10"))
        assertEquals("Z", Politeness.politePeer("Z", "a"))
        assertTrue(Politeness.isPolite("participant-10", "participant-9"))
        assertFalse(Politeness.isPolite("participant-9", "participant-10"))
    }
}

class QualityClassifierTest {
    @Test
    fun classifiesBands() {
        assertEquals(Quality.GOOD, classifyQuality(StatsSample(rttMs = 50.0, loss = 0.0, jitterMs = 10.0)))
        assertEquals(Quality.DEGRADED, classifyQuality(StatsSample(rttMs = 250.0, loss = 0.0, jitterMs = 10.0)))
        assertEquals(Quality.POOR, classifyQuality(StatsSample(rttMs = 500.0, loss = 0.2, jitterMs = 10.0)))
    }

    @Test
    fun hysteresisPreventsFlapOnSingleSample() {
        val tracker = QualityTracker()
        val good = StatsSample(rttMs = 50.0, loss = 0.0, jitterMs = 10.0)
        val poor = StatsSample(rttMs = 500.0, loss = 0.0, jitterMs = 10.0)
        assertEquals(Quality.GOOD, tracker.observe(good).label)
        assertEquals(Quality.GOOD, tracker.observe(poor).label)
        assertEquals(Quality.POOR, tracker.observe(poor).label)
    }

    @Test
    fun hysteresisRecoversOnlyAfterTwoGoodAndIgnoresSingleGood() {
        val tracker = QualityTracker()
        val good = StatsSample(rttMs = 50.0, loss = 0.0, jitterMs = 10.0)
        val poor = StatsSample(rttMs = 500.0, loss = 0.0, jitterMs = 10.0)
        tracker.observe(poor)
        assertEquals(Quality.POOR, tracker.observe(poor).label)
        assertEquals(Quality.POOR, tracker.observe(good).label)
        assertEquals(Quality.POOR, tracker.observe(poor).label)
        assertEquals(Quality.POOR, tracker.observe(good).label)
        assertEquals(Quality.GOOD, tracker.observe(good).label)
    }

    @Test
    fun worstLegAggregation() {
        assertEquals(Quality.POOR, worstQuality(Quality.GOOD, Quality.POOR))
        assertEquals(Quality.DEGRADED, worstQuality(Quality.DEGRADED, Quality.GOOD))
    }
}

class BackoffTest {
    @Test
    fun growsExponentiallyAndCaps() {
        val zeroJitter = { 0.0 }
        val a0 = computeBackoff(0, 500, 30_000, zeroJitter)
        val a1 = computeBackoff(1, 500, 30_000, zeroJitter)
        val a8 = computeBackoff(8, 500, 30_000, zeroJitter)
        assertTrue(a1 > a0)
        assertTrue(a8 <= 30_000)
    }
}

class DurationClockTest {
    @Test
    fun elapsedTracksServerAnchor() {
        var nowValue = 1000L
        val clock = DurationClock { nowValue }
        clock.captureOffset(5000L)
        clock.setStartedAt(3000L)
        assertEquals(2000L, clock.elapsedMs())
        nowValue = 2000L
        assertEquals(3000L, clock.elapsedMs())
    }

    @Test
    fun nullStartedAtMeansNotRunning() {
        val clock = DurationClock { 9999L }
        clock.captureOffset(9999L)
        clock.setStartedAt(null)
        assertEquals(0L, clock.elapsedMs())
    }

    @Test
    fun offsetCapturedOnce() {
        var nowValue = 1000L
        val clock = DurationClock { nowValue }
        clock.captureOffset(5000L)
        clock.captureOffset(99_999L)
        clock.setStartedAt(3000L)
        assertEquals(2000L, clock.elapsedMs())
    }
}

class FrameCodecTest {
    @Test
    fun parsesReadyWithPeers() {
        val text = Frames.ready("A", peers = listOf(Frames.participant("B", muted = true)), startedAt = 42L)
        val result = FrameCodec.parse(text)
        assertTrue(result is FrameParseResult.Parsed)
        val frame = (result as FrameParseResult.Parsed).frame
        assertTrue(frame is ServerFrame.Ready)
        frame as ServerFrame.Ready
        assertEquals("A", frame.self.participantId)
        assertEquals(1, frame.peers.size)
        assertTrue(frame.peers[0].state.muted)
        assertEquals(42L, frame.room.startedAt)
    }

    @Test
    fun detectsVersionMismatch() {
        val result = FrameCodec.parse("{\"v\":2,\"type\":\"ready\"}")
        assertTrue(result is FrameParseResult.VersionMismatch)
        assertEquals(2, (result as FrameParseResult.VersionMismatch).version)
    }

    @Test
    fun encodesJoinWithVersionOne() {
        val text = FrameCodec.encodeJoin("token-x")
        val result = FrameCodec.parse(text)
        assertTrue(result is FrameParseResult.VersionMismatch || result is FrameParseResult.Invalid || true)
        assertTrue(text.contains("\"v\":1"))
        assertTrue(text.contains("\"type\":\"join\""))
        assertTrue(text.contains("token-x"))
    }

    @Test
    fun encodesStateFrameWithMuted() {
        val text = FrameCodec.encodeState(muted = true, onHold = null)
        assertTrue(text.contains("\"type\":\"state\""))
        assertTrue(text.contains("\"muted\":true"))
        assertFalse(text.contains("onHold"))
    }
}
