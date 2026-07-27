package io.sauti.android.ring

import io.sauti.engine.CallPhase
import io.sauti.engine.CallState
import io.sauti.engine.ConnectionState
import io.sauti.engine.ParticipantSnapshot
import io.sauti.engine.Quality
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FakeRingbackTone : RingbackTone {
    val events = mutableListOf<String>()
    override fun start() { events += "start" }
    override fun stop() { events += "stop" }
    override fun release() { events += "release" }
}

private class FakeRingtonePlayer : RingtonePlayer {
    val events = mutableListOf<String>()
    override fun start() { events += "start" }
    override fun stop() { events += "stop" }
    override fun release() { events += "release" }
}

private class FakeCallVibrator : CallVibrator {
    val events = mutableListOf<String>()
    override fun start() { events += "start" }
    override fun stop() { events += "stop" }
}

private class FakeRingerModeProvider(var mode: RingerMode) : RingerModeProvider {
    override fun current(): RingerMode = mode
}

@OptIn(ExperimentalCoroutinesApi::class)
class SautiRingerTest {

    private fun participant(id: String): ParticipantSnapshot = ParticipantSnapshot(
        participantId = id,
        metadata = emptyMap(),
        muted = false,
        onHold = false,
        connectionState = ConnectionState.CONNECTED,
        quality = Quality.GOOD
    )

    private fun starts(events: List<String>): Int = events.count { it == "start" }

    @Test
    fun outgoingHappyPathDrivesRingbackOnly() = runTest {
        val ringback = FakeRingbackTone()
        val ringtone = FakeRingtonePlayer()
        val vibrator = FakeCallVibrator()
        val ringer = SautiRinger(backgroundScope, ringback, ringtone, vibrator, FakeRingerModeProvider(RingerMode.NORMAL))
        val flow = MutableStateFlow(CallState(phase = CallPhase.CONNECTING))

        ringer.start(CallRole.OUTGOING, flow)
        runCurrent()
        assertEquals(1, starts(ringback.events))

        flow.value = CallState(phase = CallPhase.CONNECTED)
        runCurrent()
        assertEquals(1, starts(ringback.events))

        flow.value = CallState(phase = CallPhase.CONNECTED, participants = listOf(participant("remote")))
        runCurrent()
        assertTrue(ringback.events.last() == "stop")
        assertEquals(0, starts(ringtone.events))
        assertEquals(0, starts(vibrator.events))
    }

    @Test
    fun incomingNormalStartsRingtoneAndVibratorThenStopsOnConnected() = runTest {
        val ringback = FakeRingbackTone()
        val ringtone = FakeRingtonePlayer()
        val vibrator = FakeCallVibrator()
        val ringer = SautiRinger(backgroundScope, ringback, ringtone, vibrator, FakeRingerModeProvider(RingerMode.NORMAL))
        val flow = MutableStateFlow(CallState(phase = CallPhase.IDLE))

        ringer.start(CallRole.INCOMING, flow)
        runCurrent()
        assertEquals(1, starts(ringtone.events))
        assertEquals(1, starts(vibrator.events))
        assertEquals(0, starts(ringback.events))

        flow.value = CallState(phase = CallPhase.CONNECTED)
        runCurrent()
        assertTrue(ringtone.events.last() == "stop")
        assertTrue(vibrator.events.last() == "stop")
    }

    @Test
    fun incomingVibrateStartsVibratorOnly() = runTest {
        val ringback = FakeRingbackTone()
        val ringtone = FakeRingtonePlayer()
        val vibrator = FakeCallVibrator()
        val ringer = SautiRinger(backgroundScope, ringback, ringtone, vibrator, FakeRingerModeProvider(RingerMode.VIBRATE))
        val flow = MutableStateFlow(CallState(phase = CallPhase.IDLE))

        ringer.start(CallRole.INCOMING, flow)
        runCurrent()
        assertEquals(1, starts(vibrator.events))
        assertEquals(0, starts(ringtone.events))
        assertEquals(0, starts(ringback.events))
    }

    @Test
    fun incomingSilentStartsNothing() = runTest {
        val ringback = FakeRingbackTone()
        val ringtone = FakeRingtonePlayer()
        val vibrator = FakeCallVibrator()
        val ringer = SautiRinger(backgroundScope, ringback, ringtone, vibrator, FakeRingerModeProvider(RingerMode.SILENT))
        val flow = MutableStateFlow(CallState(phase = CallPhase.IDLE))

        ringer.start(CallRole.INCOMING, flow)
        runCurrent()
        assertEquals(0, starts(ringback.events))
        assertEquals(0, starts(ringtone.events))
        assertEquals(0, starts(vibrator.events))
    }

    @Test
    fun repeatedIdenticalModeStartsRingbackOnce() = runTest {
        val ringback = FakeRingbackTone()
        val ringer = SautiRinger(backgroundScope, ringback, FakeRingtonePlayer(), FakeCallVibrator(), FakeRingerModeProvider(RingerMode.NORMAL))
        val flow = MutableStateFlow(CallState(phase = CallPhase.CONNECTING, durationMs = 1))

        ringer.start(CallRole.OUTGOING, flow)
        runCurrent()
        flow.value = CallState(phase = CallPhase.CONNECTING, durationMs = 2)
        runCurrent()
        flow.value = CallState(phase = CallPhase.CONNECTING, durationMs = 3)
        runCurrent()

        assertEquals(1, starts(ringback.events))
        assertEquals(listOf("start"), ringback.events)
    }

    @Test
    fun ringbackSilentRingbackProducesStartStopStart() = runTest {
        val ringback = FakeRingbackTone()
        val ringer = SautiRinger(backgroundScope, ringback, FakeRingtonePlayer(), FakeCallVibrator(), FakeRingerModeProvider(RingerMode.NORMAL))
        val flow = MutableStateFlow(CallState(phase = CallPhase.CONNECTING))

        ringer.start(CallRole.OUTGOING, flow)
        runCurrent()
        flow.value = CallState(phase = CallPhase.CONNECTED, participants = listOf(participant("remote")))
        runCurrent()
        flow.value = CallState(phase = CallPhase.CONNECTING)
        runCurrent()

        assertEquals(listOf("start", "stop", "start"), ringback.events)
    }

    @Test
    fun stopReleasesPortsAndHaltsFurtherActuation() = runTest {
        val ringback = FakeRingbackTone()
        val ringtone = FakeRingtonePlayer()
        val vibrator = FakeCallVibrator()
        val ringer = SautiRinger(backgroundScope, ringback, ringtone, vibrator, FakeRingerModeProvider(RingerMode.NORMAL))
        val flow = MutableStateFlow(CallState(phase = CallPhase.CONNECTING))

        ringer.start(CallRole.OUTGOING, flow)
        runCurrent()
        ringer.stop()

        assertTrue(ringback.events.contains("release"))
        assertTrue(ringtone.events.contains("release"))
        assertEquals("stop", vibrator.events.last())

        val ringbackAfter = ringback.events.size
        flow.value = CallState(phase = CallPhase.CONNECTED)
        runCurrent()
        assertEquals(ringbackAfter, ringback.events.size)
    }

    @Test
    fun stopIsSafeBeforeStartAndWhenCalledTwice() = runTest {
        val ringback = FakeRingbackTone()
        val ringtone = FakeRingtonePlayer()
        val ringer = SautiRinger(backgroundScope, ringback, ringtone, FakeCallVibrator(), FakeRingerModeProvider(RingerMode.NORMAL))

        ringer.stop()
        ringer.stop()

        assertTrue(ringback.events.contains("release"))
        assertTrue(ringtone.events.contains("release"))
    }

    @Test
    fun secondStartCancelsFirstSubscription() = runTest {
        val ringback = FakeRingbackTone()
        val ringtone = FakeRingtonePlayer()
        val vibrator = FakeCallVibrator()
        val ringer = SautiRinger(backgroundScope, ringback, ringtone, vibrator, FakeRingerModeProvider(RingerMode.NORMAL))
        val outgoing = MutableStateFlow(CallState(phase = CallPhase.CONNECTING))
        val incoming = MutableStateFlow(CallState(phase = CallPhase.IDLE))

        ringer.start(CallRole.OUTGOING, outgoing)
        runCurrent()
        assertEquals(1, starts(ringback.events))

        ringer.start(CallRole.INCOMING, incoming)
        runCurrent()

        val ringbackStartsBefore = starts(ringback.events)
        outgoing.value = CallState(phase = CallPhase.CONNECTED)
        runCurrent()
        assertEquals(ringbackStartsBefore, starts(ringback.events))
        assertEquals(1, starts(ringtone.events))
    }
}
