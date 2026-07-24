package io.sauti.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

private class Harness(
    val rtc: FakeRtcFactory,
    val hub: FakeTransportHub,
    val media: RecordingLocalMedia,
    val session: CallSession,
    val clock: () -> Long,
    val setClock: (Long) -> Unit
)

private fun buildHarness(scope: CoroutineScope, startClock: Long = 1000L): Harness {
    var nowValue = startClock
    val rtc = FakeRtcFactory()
    val hub = FakeTransportHub()
    val media = RecordingLocalMedia()
    val session = CallSession(
        rtcFactory = rtc,
        transportFactory = hub,
        localMedia = media,
        config = EngineConfig(
            now = { nowValue },
            random = { 0.0 },
            statsIntervalMs = 2_000
        ),
        scope = scope
    )
    return Harness(rtc, hub, media, session, { nowValue }, { nowValue = it })
}

class CallSessionTest {
    @Test
    fun joinPopulatesParticipantsAndSendsTokenJoin() = runTest {
        val h = buildHarness(backgroundScope)
        backgroundScope.launch { h.session.join(JoinConfig("wss://x", "token-A")) }
        runCurrent()
        h.hub.latest.deliver(Frames.ready("A", peers = listOf(Frames.participant("B", muted = true))))
        runCurrent()

        val state = h.session.state.value
        assertEquals(CallPhase.CONNECTED, state.phase)
        assertEquals(setOf("A", "B"), state.participants.map { it.participantId }.toSet())
        val b = state.participants.first { it.participantId == "B" }
        assertTrue(b.muted)
        assertEquals(listOf("token-A"), h.hub.joinTokens())
    }

    @Test
    fun threeParticipantRoomCreatesTwoPeersOffererByPoliteness() = runTest {
        val h = buildHarness(backgroundScope)
        backgroundScope.launch { h.session.join(JoinConfig("wss://x", "t")) }
        runCurrent()
        h.hub.latest.deliver(
            Frames.ready("m", peers = listOf(Frames.participant("a"), Frames.participant("z")))
        )
        runCurrent()

        assertEquals(2, h.rtc.created.size)
        val offerToA = h.rtc.created[0]
        val offerToZ = h.rtc.created[1]
        assertTrue(offerToA.createdOffers >= 1)
        assertEquals(0, offerToZ.createdOffers)

        h.hub.latest.deliver(Frames.participantLeft("a"))
        runCurrent()
        assertTrue(offerToA.closed)
        assertEquals(setOf("m", "z"), h.session.state.value.participants.map { it.participantId }.toSet())
    }

    @Test
    fun addressedOfferProducesAnswerAndIceIsApplied() = runTest {
        val h = buildHarness(backgroundScope)
        backgroundScope.launch { h.session.join(JoinConfig("wss://x", "t")) }
        runCurrent()
        h.hub.latest.deliver(Frames.ready("a", peers = listOf(Frames.participant("b"))))
        runCurrent()

        val pc = h.rtc.created.first()
        h.hub.latest.deliver(Frames.offer("b", "remote-offer"))
        runCurrent()
        assertEquals("remote-offer", pc.remoteDescription?.sdp)

        val answers = h.hub.latest.sent.filter { FakeTransportHub.typeOf(it) == "answer" }
        assertTrue(answers.isNotEmpty())
        assertTrue(answers.first().contains("\"to\":\"b\""))

        h.hub.latest.deliver(Frames.ice("b", "cand-1"))
        runCurrent()
        assertEquals("cand-1", pc.addedCandidates.first().candidate)
    }

    @Test
    fun muteSendsStateAndInboundStateUpdatesPeer() = runTest {
        val h = buildHarness(backgroundScope)
        backgroundScope.launch { h.session.join(JoinConfig("wss://x", "t")) }
        runCurrent()
        h.hub.latest.deliver(Frames.ready("a", peers = listOf(Frames.participant("b"))))
        runCurrent()

        h.session.setMuted(true)
        runCurrent()
        val stateFrames = h.hub.latest.sent.filter { FakeTransportHub.typeOf(it) == "state" }
        assertTrue(stateFrames.any { it.contains("\"muted\":true") })
        assertTrue(h.session.state.value.localMuted)
        assertTrue(h.media.enabledHistory.contains(false))

        h.hub.latest.deliver(Frames.participantState("b", muted = true))
        runCurrent()
        val b = h.session.state.value.participants.first { it.participantId == "b" }
        assertTrue(b.muted)
    }

    @Test
    fun readyPeerMuteSeedsInitialStateBeforeChangeFrame() = runTest {
        val h = buildHarness(backgroundScope)
        backgroundScope.launch { h.session.join(JoinConfig("wss://x", "t")) }
        runCurrent()
        h.hub.latest.deliver(Frames.ready("a", peers = listOf(Frames.participant("b", muted = true))))
        runCurrent()
        val b = h.session.state.value.participants.first { it.participantId == "b" }
        assertTrue(b.muted)
    }

    @Test
    fun holdReusesStatePlumbing() = runTest {
        val h = buildHarness(backgroundScope)
        backgroundScope.launch { h.session.join(JoinConfig("wss://x", "t")) }
        runCurrent()
        h.hub.latest.deliver(Frames.ready("a", peers = listOf(Frames.participant("b"))))
        runCurrent()

        h.session.setHold(true)
        runCurrent()
        val stateFrames = h.hub.latest.sent.filter { FakeTransportHub.typeOf(it) == "state" }
        assertTrue(stateFrames.any { it.contains("\"onHold\":true") })

        h.hub.latest.deliver(Frames.participantState("b", onHold = true))
        runCurrent()
        val b = h.session.state.value.participants.first { it.participantId == "b" }
        assertTrue(b.onHold)
    }

    @Test
    fun durationIsServerAnchoredAndSurvivesResume() = runTest {
        val h = buildHarness(backgroundScope, startClock = 1000L)
        backgroundScope.launch { h.session.join(JoinConfig("wss://x", "token-A")) }
        runCurrent()
        h.hub.latest.deliver(Frames.ready("a", peers = listOf(Frames.participant("b")), serverNow = 5000L))
        runCurrent()
        val pc = h.rtc.created.first()

        h.hub.latest.deliver(Frames.roomStarted(3000L))
        runCurrent()
        advanceTimeBy(1_001)
        runCurrent()
        assertEquals(2000L, h.session.state.value.durationMs)

        h.setClock(2000L)
        h.hub.latest.dropByServer()
        runCurrent()
        assertTrue(h.session.state.value.reconnecting)

        advanceTimeBy(2_000)
        runCurrent()
        h.hub.latest.deliver(
            Frames.ready("a", peers = listOf(Frames.participant("b")), startedAt = 3000L, serverNow = 6000L, resumed = true)
        )
        runCurrent()

        assertSame(pc, h.rtc.created.first())
        assertEquals(1, h.rtc.created.size)
        assertFalse(pc.closed)
        assertEquals(3000L, h.session.state.value.durationMs)
    }

    @Test
    fun reconnectRePresentsSameTokenWithoutLeave() = runTest {
        val h = buildHarness(backgroundScope)
        backgroundScope.launch { h.session.join(JoinConfig("wss://x", "token-A")) }
        runCurrent()
        h.hub.latest.deliver(Frames.ready("a", peers = listOf(Frames.participant("b"))))
        runCurrent()

        val firstTransport = h.hub.latest
        firstTransport.dropByServer()
        runCurrent()
        advanceTimeBy(2_000)
        runCurrent()

        assertTrue(h.hub.transports.size >= 2)
        assertEquals(listOf("token-A", "token-A"), h.hub.joinTokens())
        assertFalse(firstTransport.sent.any { FakeTransportHub.typeOf(it) == "leave" })
    }

    @Test
    fun resumePrefersIceRestartOnExistingPeer() = runTest {
        val h = buildHarness(backgroundScope)
        backgroundScope.launch { h.session.join(JoinConfig("wss://x", "token-A")) }
        runCurrent()
        h.hub.latest.deliver(Frames.ready("a", peers = listOf(Frames.participant("b"))))
        runCurrent()
        val pc = h.rtc.created.first()

        h.hub.latest.dropByServer()
        runCurrent()
        advanceTimeBy(2_000)
        runCurrent()
        h.hub.latest.deliver(
            Frames.ready("a", peers = listOf(Frames.participant("b")), resumed = true)
        )
        runCurrent()

        assertEquals(1, h.rtc.created.size)
        assertTrue(pc.restartIceCalls >= 1)
    }

    @Test
    fun unreachableFrameMarksPeerReconnecting() = runTest {
        val h = buildHarness(backgroundScope)
        backgroundScope.launch { h.session.join(JoinConfig("wss://x", "t")) }
        runCurrent()
        h.hub.latest.deliver(Frames.ready("a", peers = listOf(Frames.participant("b"))))
        runCurrent()

        h.hub.latest.deliver(Frames.participantUnreachable("b"))
        runCurrent()
        val b = h.session.state.value.participants.first { it.participantId == "b" }
        assertEquals(ConnectionState.RECONNECTING, b.connectionState)
        assertTrue(h.session.state.value.reconnecting)
    }

    @Test
    fun poorPeerDegradesAggregateQuality() = runTest {
        val h = buildHarness(backgroundScope)
        backgroundScope.launch { h.session.join(JoinConfig("wss://x", "t")) }
        runCurrent()
        h.hub.latest.deliver(Frames.ready("a", peers = listOf(Frames.participant("b"))))
        runCurrent()

        val pc = h.rtc.created.first()
        pc.stats = StatsSample(rttMs = 800.0, loss = 0.3, jitterMs = 200.0)

        h.session.pollQuality()
        h.session.pollQuality()
        runCurrent()

        val b = h.session.state.value.participants.first { it.participantId == "b" }
        assertEquals(Quality.POOR, b.quality)
        assertEquals(Quality.POOR, h.session.state.value.quality)
    }

    @Test
    fun networkChangeTriggersIceRestartAll() = runTest {
        val h = buildHarness(backgroundScope)
        backgroundScope.launch { h.session.join(JoinConfig("wss://x", "t")) }
        runCurrent()
        h.hub.latest.deliver(Frames.ready("a", peers = listOf(Frames.participant("b"))))
        runCurrent()
        val pc = h.rtc.created.first()

        h.session.onNetworkChanged()
        runCurrent()
        assertTrue(pc.restartIceCalls >= 1)
    }

    @Test
    fun participantJoinedAddsPeerMidCall() = runTest {
        val h = buildHarness(backgroundScope)
        backgroundScope.launch { h.session.join(JoinConfig("wss://x", "t")) }
        runCurrent()
        h.hub.latest.deliver(Frames.ready("a", peers = listOf(Frames.participant("b"))))
        runCurrent()
        assertEquals(1, h.rtc.created.size)

        h.hub.latest.deliver(Frames.participantJoined("c"))
        runCurrent()

        assertEquals(2, h.rtc.created.size)
        assertEquals(setOf("a", "b", "c"), h.session.state.value.participants.map { it.participantId }.toSet())
    }

    @Test
    fun participantJoinedBeyondCapIsIgnored() = runTest {
        val h = buildHarness(backgroundScope)
        backgroundScope.launch { h.session.join(JoinConfig("wss://x", "t")) }
        runCurrent()
        h.hub.latest.deliver(
            Frames.ready("a", peers = listOf(Frames.participant("b"), Frames.participant("c")), maxParticipants = 3)
        )
        runCurrent()
        assertEquals(2, h.rtc.created.size)

        h.hub.latest.deliver(Frames.participantJoined("d"))
        runCurrent()

        assertEquals(2, h.rtc.created.size)
        assertEquals(setOf("a", "b", "c"), h.session.state.value.participants.map { it.participantId }.toSet())
    }

    @Test
    fun readyAtCapacityRejectsJoin() = runTest {
        val h = buildHarness(backgroundScope)
        val failures = mutableListOf<SautiException>()
        backgroundScope.launch { h.session.eventFlow.collect { if (it is CallEvent.Failure) failures.add(it.error) } }
        backgroundScope.launch { runCatching { h.session.join(JoinConfig("wss://x", "t")) } }
        runCurrent()
        h.hub.latest.deliver(
            Frames.ready(
                "a",
                peers = listOf(Frames.participant("b"), Frames.participant("c"), Frames.participant("d")),
                maxParticipants = 3
            )
        )
        runCurrent()

        assertTrue(failures.any { it is RoomFullException })
        assertEquals(0, h.rtc.created.size)
    }

    @Test
    fun unrecoverablePeerIsRebuiltWithFreshConnection() = runTest {
        val h = buildHarness(backgroundScope)
        backgroundScope.launch { h.session.join(JoinConfig("wss://x", "t")) }
        runCurrent()
        h.hub.latest.deliver(Frames.ready("a", peers = listOf(Frames.participant("b"))))
        runCurrent()
        val original = h.rtc.created.first()

        original.emitFailed()
        runCurrent()
        assertEquals(1, h.rtc.created.size)
        assertTrue(original.restartIceCalls >= 1)

        original.emitFailed()
        runCurrent()
        assertEquals(2, h.rtc.created.size)
        assertTrue(original.closed)
        assertFalse(h.rtc.created[1].closed)
    }

    @Test
    fun qualityPollFiresOnTwoSecondCadence() = runTest {
        val h = buildHarness(backgroundScope)
        backgroundScope.launch { h.session.join(JoinConfig("wss://x", "t")) }
        runCurrent()
        h.hub.latest.deliver(Frames.ready("a", peers = listOf(Frames.participant("b"))))
        runCurrent()

        val pc = h.rtc.created.first()
        pc.stats = StatsSample(rttMs = 800.0, loss = 0.3, jitterMs = 200.0)

        advanceTimeBy(1_999)
        runCurrent()
        assertEquals(Quality.GOOD, h.session.state.value.quality)

        advanceTimeBy(2)
        runCurrent()
        assertEquals(Quality.GOOD, h.session.state.value.quality)

        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(Quality.POOR, h.session.state.value.quality)
    }

    @Test
    fun localMediaControllerIsNotNullOnMute() = runTest {
        val h = buildHarness(backgroundScope)
        backgroundScope.launch { h.session.join(JoinConfig("wss://x", "t")) }
        runCurrent()
        h.hub.latest.deliver(Frames.ready("a"))
        runCurrent()
        h.session.setMuted(true)
        assertNotNull(h.media.enabledHistory.lastOrNull())
    }
}
