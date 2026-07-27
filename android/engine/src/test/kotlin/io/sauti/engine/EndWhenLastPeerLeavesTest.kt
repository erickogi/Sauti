package io.sauti.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class LastPeerHarness(
    val rtc: FakeRtcFactory,
    val hub: FakeTransportHub,
    val session: CallSession
)

private fun buildLastPeerHarness(scope: CoroutineScope): LastPeerHarness {
    var nowValue = 1000L
    val rtc = FakeRtcFactory()
    val hub = FakeTransportHub()
    val session = CallSession(
        rtcFactory = rtc,
        transportFactory = hub,
        localMedia = RecordingLocalMedia(),
        config = EngineConfig(
            now = { nowValue },
            random = { 0.0 },
            statsIntervalMs = 2_000
        ),
        scope = scope
    )
    return LastPeerHarness(rtc, hub, session)
}

private fun FakeTransportHub.leaveWasSent(): Boolean = sentTypes().contains("leave")

class EndWhenLastPeerLeavesTest {
    @Test
    fun defaultOffPeerLeaveDoesNotEnd() = runTest {
        val h = buildLastPeerHarness(backgroundScope)
        backgroundScope.launch { h.session.join(JoinConfig("wss://x", "t")) }
        runCurrent()
        h.hub.latest.deliver(Frames.ready("a", peers = listOf(Frames.participant("b"))))
        runCurrent()

        h.hub.latest.deliver(Frames.participantLeft("b"))
        runCurrent()

        assertFalse(h.hub.leaveWasSent())
        assertEquals(CallPhase.CONNECTED, h.session.state.value.phase)
        assertTrue(h.session.state.value.participants.any { it.participantId == "a" })
    }

    @Test
    fun enabledLastPeerLeaveEnds() = runTest {
        val h = buildLastPeerHarness(backgroundScope)
        backgroundScope.launch { h.session.join(JoinConfig("wss://x", "t", endWhenLastPeerLeaves = true)) }
        runCurrent()
        h.hub.latest.deliver(Frames.ready("a", peers = listOf(Frames.participant("b"))))
        runCurrent()

        h.hub.latest.deliver(Frames.participantLeft("b"))
        runCurrent()

        assertTrue(h.hub.leaveWasSent())
        assertEquals(CallPhase.LEFT, h.session.state.value.phase)
    }

    @Test
    fun enabledSelfAloneFromStartDoesNotEnd() = runTest {
        val h = buildLastPeerHarness(backgroundScope)
        backgroundScope.launch { h.session.join(JoinConfig("wss://x", "t", endWhenLastPeerLeaves = true)) }
        runCurrent()
        h.hub.latest.deliver(Frames.ready("a"))
        runCurrent()

        assertFalse(h.hub.leaveWasSent())
        assertEquals(CallPhase.CONNECTED, h.session.state.value.phase)
    }

    @Test
    fun enabledOnlyLastOfTwoLeavesEnds() = runTest {
        val h = buildLastPeerHarness(backgroundScope)
        backgroundScope.launch { h.session.join(JoinConfig("wss://x", "t", endWhenLastPeerLeaves = true)) }
        runCurrent()
        h.hub.latest.deliver(
            Frames.ready("a", peers = listOf(Frames.participant("b"), Frames.participant("c")))
        )
        runCurrent()

        h.hub.latest.deliver(Frames.participantLeft("b"))
        runCurrent()
        assertFalse(h.hub.leaveWasSent())
        assertEquals(CallPhase.CONNECTED, h.session.state.value.phase)

        h.hub.latest.deliver(Frames.participantLeft("c"))
        runCurrent()
        assertTrue(h.hub.leaveWasSent())
        assertEquals(CallPhase.LEFT, h.session.state.value.phase)
    }

    @Test
    fun enabledUnreachableDoesNotEnd() = runTest {
        val h = buildLastPeerHarness(backgroundScope)
        backgroundScope.launch { h.session.join(JoinConfig("wss://x", "t", endWhenLastPeerLeaves = true)) }
        runCurrent()
        h.hub.latest.deliver(Frames.ready("a", peers = listOf(Frames.participant("b"))))
        runCurrent()

        h.hub.latest.deliver(Frames.participantUnreachable("b"))
        runCurrent()

        assertFalse(h.hub.leaveWasSent())
        assertEquals(CallPhase.CONNECTED, h.session.state.value.phase)
        val b = h.session.state.value.participants.first { it.participantId == "b" }
        assertEquals(ConnectionState.RECONNECTING, b.connectionState)
    }

    @Test
    fun enabledPeerVanishesOnResumeEnds() = runTest {
        val h = buildLastPeerHarness(backgroundScope)
        backgroundScope.launch { h.session.join(JoinConfig("wss://x", "t", endWhenLastPeerLeaves = true)) }
        runCurrent()
        h.hub.latest.deliver(Frames.ready("a", peers = listOf(Frames.participant("b"))))
        runCurrent()

        h.hub.latest.dropByServer()
        runCurrent()
        advanceTimeBy(2_000)
        runCurrent()
        h.hub.latest.deliver(Frames.ready("a", peers = emptyList(), resumed = true))
        runCurrent()

        assertTrue(h.hub.leaveWasSent())
        assertEquals(CallPhase.LEFT, h.session.state.value.phase)
    }

    @Test
    fun enabledMidCallJoinThenLeaveEnds() = runTest {
        val h = buildLastPeerHarness(backgroundScope)
        backgroundScope.launch { h.session.join(JoinConfig("wss://x", "t", endWhenLastPeerLeaves = true)) }
        runCurrent()
        h.hub.latest.deliver(Frames.ready("a"))
        runCurrent()
        assertFalse(h.hub.leaveWasSent())
        assertEquals(CallPhase.CONNECTED, h.session.state.value.phase)

        h.hub.latest.deliver(Frames.participantJoined("c"))
        runCurrent()
        assertFalse(h.hub.leaveWasSent())

        h.hub.latest.deliver(Frames.participantLeft("c"))
        runCurrent()

        assertTrue(h.hub.leaveWasSent())
        assertEquals(CallPhase.LEFT, h.session.state.value.phase)
    }
}
