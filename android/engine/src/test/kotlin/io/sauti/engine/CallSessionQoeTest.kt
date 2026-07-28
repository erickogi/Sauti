package io.sauti.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class QoeHarness(
    val rtc: FakeRtcFactory,
    val hub: FakeTransportHub,
    val session: CallSession,
    val samples: MutableList<QoeSample>,
    val setClock: (Long) -> Unit
)

private fun buildQoeHarness(
    scope: CoroutineScope,
    startClock: Long = 1000L,
    onQoe: ((QoeSample) -> Unit)?
): QoeHarness {
    var nowValue = startClock
    val rtc = FakeRtcFactory()
    val hub = FakeTransportHub()
    val samples = mutableListOf<QoeSample>()
    val session = CallSession(
        rtcFactory = rtc,
        transportFactory = hub,
        localMedia = RecordingLocalMedia(),
        config = EngineConfig(
            now = { nowValue },
            random = { 0.0 },
            statsIntervalMs = 2_000,
            onQoe = onQoe
        ),
        scope = scope
    )
    return QoeHarness(rtc, hub, session, samples, { nowValue = it })
}

private fun QoeHarness.deliverReady(vararg peers: String) {
    hub.latest.deliver(Frames.ready("a", peers = peers.map { Frames.participant(it) }))
}

class CallSessionQoeTest {
    @Test
    fun onQoeReceivesOneSamplePerPeerPerPoll() = runTest {
        lateinit var h: QoeHarness
        h = buildQoeHarness(backgroundScope) { h.samples.add(it) }
        backgroundScope.launch { h.session.join(JoinConfig("wss://x", "t")) }
        runCurrent()
        h.deliverReady("b", "c")
        runCurrent()

        h.setClock(7777L)
        h.session.pollQuality()
        runCurrent()

        assertEquals(2, h.samples.size)
        assertEquals(setOf("b", "c"), h.samples.map { it.participantId }.toSet())
        assertTrue(h.samples.all { it.sampledAt == 7777L })
    }

    @Test
    fun onQoeCarriesClassifierNumbersByValue() = runTest {
        lateinit var h: QoeHarness
        h = buildQoeHarness(backgroundScope) { h.samples.add(it) }
        backgroundScope.launch { h.session.join(JoinConfig("wss://x", "t")) }
        runCurrent()
        h.deliverReady("b")
        runCurrent()
        h.rtc.created.first().stats = StatsSample(rttMs = 123.0, loss = 0.05, jitterMs = 42.0)

        h.session.pollQuality()
        runCurrent()

        val sample = h.samples.single()
        assertEquals(123.0, sample.rttMs)
        assertEquals(0.05, sample.loss)
        assertEquals(42.0, sample.jitterMs)
    }

    @Test
    fun onQoePassesExtrasThroughWhenPresent() = runTest {
        lateinit var h: QoeHarness
        h = buildQoeHarness(backgroundScope) { h.samples.add(it) }
        backgroundScope.launch { h.session.join(JoinConfig("wss://x", "t")) }
        runCurrent()
        h.deliverReady("b")
        runCurrent()
        h.rtc.created.first().stats = StatsSample(
            rttMs = 10.0,
            loss = 0.0,
            jitterMs = 5.0,
            audioLevel = 0.7,
            jitterBufferMs = 33.0,
            fractionLost = 0.02
        )

        h.session.pollQuality()
        runCurrent()

        val sample = h.samples.single()
        assertEquals(0.7, sample.audioLevel)
        assertEquals(33.0, sample.jitterBufferMs)
        assertEquals(0.02, sample.fractionLost)
    }

    @Test
    fun onQoeReportsNullExtrasWhenAbsent() = runTest {
        lateinit var h: QoeHarness
        h = buildQoeHarness(backgroundScope) { h.samples.add(it) }
        backgroundScope.launch { h.session.join(JoinConfig("wss://x", "t")) }
        runCurrent()
        h.deliverReady("b")
        runCurrent()

        h.session.pollQuality()
        runCurrent()

        val sample = h.samples.single()
        assertNull(sample.audioLevel)
        assertNull(sample.jitterBufferMs)
        assertNull(sample.fractionLost)
    }

    @Test
    fun onQoeSkipsPeerWhenStatsThrow() = runTest {
        lateinit var h: QoeHarness
        h = buildQoeHarness(backgroundScope) { h.samples.add(it) }
        backgroundScope.launch { h.session.join(JoinConfig("wss://x", "t")) }
        runCurrent()
        h.deliverReady("b", "c")
        runCurrent()
        h.rtc.created[0].throwOnStats = true

        h.session.pollQuality()
        runCurrent()

        assertEquals(listOf("c"), h.samples.map { it.participantId })
    }

    @Test
    fun nullOnQoeEmitsNoSampleAndPreservesQualityChanged() = runTest {
        val h = buildQoeHarness(backgroundScope, onQoe = null)
        val events = mutableListOf<CallEvent>()
        backgroundScope.launch { h.session.eventFlow.collect { events.add(it) } }
        backgroundScope.launch { h.session.join(JoinConfig("wss://x", "t")) }
        runCurrent()
        h.deliverReady("b")
        runCurrent()
        h.rtc.created.first().stats = StatsSample(rttMs = 800.0, loss = 0.3, jitterMs = 200.0)

        h.session.pollQuality()
        h.session.pollQuality()
        runCurrent()

        assertEquals(0, h.samples.size)
        assertEquals(Quality.POOR, h.session.state.value.participants.first { it.participantId == "b" }.quality)
        assertTrue(events.any { it is CallEvent.QualityChanged && it.quality == Quality.POOR })
    }

    @Test
    fun highLossSampleUpdatesQualityButNeverEmitsUnreachable() = runTest {
        lateinit var h: QoeHarness
        h = buildQoeHarness(backgroundScope) { h.samples.add(it) }
        val events = mutableListOf<CallEvent>()
        backgroundScope.launch { h.session.eventFlow.collect { events.add(it) } }
        backgroundScope.launch { h.session.join(JoinConfig("wss://x", "t")) }
        runCurrent()
        h.deliverReady("b")
        runCurrent()
        h.rtc.created.first().stats = StatsSample(rttMs = 20.0, loss = 0.3, jitterMs = 10.0)

        h.session.pollQuality()
        h.session.pollQuality()
        runCurrent()

        assertEquals(
            Quality.POOR,
            h.session.state.value.participants.first { it.participantId == "b" }.quality
        )
        assertTrue(events.any { it is CallEvent.QualityChanged && it.quality == Quality.POOR })
        assertTrue(events.none { it is CallEvent.Unreachable })
    }

    @Test
    fun classificationIsUnchangedWhenExtrasArePresent() = runTest {
        lateinit var h: QoeHarness
        h = buildQoeHarness(backgroundScope) { h.samples.add(it) }
        backgroundScope.launch { h.session.join(JoinConfig("wss://x", "t")) }
        runCurrent()
        h.deliverReady("b")
        runCurrent()
        h.rtc.created.first().stats = StatsSample(
            rttMs = 800.0,
            loss = 0.3,
            jitterMs = 200.0,
            audioLevel = 0.9,
            jitterBufferMs = 12.0,
            fractionLost = 0.5
        )

        h.session.pollQuality()
        h.session.pollQuality()
        runCurrent()

        val expected = classifyQuality(StatsSample(rttMs = 800.0, loss = 0.3, jitterMs = 200.0))
        assertEquals(Quality.POOR, expected)
        assertEquals(expected, h.session.state.value.participants.first { it.participantId == "b" }.quality)
    }
}
