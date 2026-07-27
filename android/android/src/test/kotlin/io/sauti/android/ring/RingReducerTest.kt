package io.sauti.android.ring

import io.sauti.engine.CallPhase
import io.sauti.engine.CallState
import io.sauti.engine.ConnectionState
import io.sauti.engine.ParticipantSnapshot
import io.sauti.engine.Quality
import kotlin.test.Test
import kotlin.test.assertEquals

class RingReducerTest {

    private fun participant(id: String): ParticipantSnapshot = ParticipantSnapshot(
        participantId = id,
        metadata = emptyMap(),
        muted = false,
        onHold = false,
        connectionState = ConnectionState.CONNECTED,
        quality = Quality.GOOD
    )

    private fun state(phase: CallPhase, participants: List<ParticipantSnapshot> = emptyList()): CallState =
        CallState(phase = phase, participants = participants)

    @Test
    fun outgoingConnectingRingsBack() {
        val mode = RingReducer.reduce(CallRole.OUTGOING, state(CallPhase.CONNECTING), null, RingerMode.NORMAL)
        assertEquals(RingMode.Ringback, mode)
    }

    @Test
    fun outgoingConnectedAloneRingsBack() {
        val mode = RingReducer.reduce(CallRole.OUTGOING, state(CallPhase.CONNECTED), null, RingerMode.NORMAL)
        assertEquals(RingMode.Ringback, mode)
    }

    @Test
    fun outgoingRingbackIgnoresRingerMode() {
        for (ringerMode in RingerMode.values()) {
            val mode = RingReducer.reduce(CallRole.OUTGOING, state(CallPhase.CONNECTING), null, ringerMode)
            assertEquals(RingMode.Ringback, mode)
        }
    }

    @Test
    fun outgoingConnectedWithRemoteIsSilent() {
        val mode = RingReducer.reduce(
            CallRole.OUTGOING,
            state(CallPhase.CONNECTED, listOf(participant("remote"))),
            null,
            RingerMode.NORMAL
        )
        assertEquals(RingMode.Silent, mode)
    }

    @Test
    fun outgoingIdleIsSilent() {
        val mode = RingReducer.reduce(CallRole.OUTGOING, state(CallPhase.IDLE), null, RingerMode.NORMAL)
        assertEquals(RingMode.Silent, mode)
    }

    @Test
    fun outgoingLeftIsSilent() {
        val mode = RingReducer.reduce(CallRole.OUTGOING, state(CallPhase.LEFT), null, RingerMode.NORMAL)
        assertEquals(RingMode.Silent, mode)
    }

    @Test
    fun incomingNormalIdleSoundsAndVibrates() {
        val mode = RingReducer.reduce(CallRole.INCOMING, state(CallPhase.IDLE), null, RingerMode.NORMAL)
        assertEquals(RingMode.Incoming(sound = true), mode)
    }

    @Test
    fun incomingNormalConnectingSoundsAndVibrates() {
        val mode = RingReducer.reduce(CallRole.INCOMING, state(CallPhase.CONNECTING), null, RingerMode.NORMAL)
        assertEquals(RingMode.Incoming(sound = true), mode)
    }

    @Test
    fun incomingVibrateIdleVibratesOnly() {
        val mode = RingReducer.reduce(CallRole.INCOMING, state(CallPhase.IDLE), null, RingerMode.VIBRATE)
        assertEquals(RingMode.Incoming(sound = false), mode)
    }

    @Test
    fun incomingSilentIdleIsSilent() {
        val mode = RingReducer.reduce(CallRole.INCOMING, state(CallPhase.IDLE), null, RingerMode.SILENT)
        assertEquals(RingMode.Silent, mode)
    }

    @Test
    fun incomingSilentConnectingIsSilent() {
        val mode = RingReducer.reduce(CallRole.INCOMING, state(CallPhase.CONNECTING), null, RingerMode.SILENT)
        assertEquals(RingMode.Silent, mode)
    }

    @Test
    fun incomingConnectedIsSilent() {
        val mode = RingReducer.reduce(CallRole.INCOMING, state(CallPhase.CONNECTED), null, RingerMode.NORMAL)
        assertEquals(RingMode.Silent, mode)
    }

    @Test
    fun incomingLeftIsSilent() {
        val mode = RingReducer.reduce(CallRole.INCOMING, state(CallPhase.LEFT), null, RingerMode.NORMAL)
        assertEquals(RingMode.Silent, mode)
    }

    @Test
    fun selfParticipantIsExcludedFromRemotePresence() {
        val mode = RingReducer.reduce(
            CallRole.OUTGOING,
            state(CallPhase.CONNECTED, listOf(participant("self"))),
            "self",
            RingerMode.NORMAL
        )
        assertEquals(RingMode.Ringback, mode)
    }

    @Test
    fun distinctParticipantCountsAsRemote() {
        val mode = RingReducer.reduce(
            CallRole.OUTGOING,
            state(CallPhase.CONNECTED, listOf(participant("other"))),
            "self",
            RingerMode.NORMAL
        )
        assertEquals(RingMode.Silent, mode)
    }

    @Test
    fun nullSelfTreatsAnyParticipantAsRemote() {
        val mode = RingReducer.reduce(
            CallRole.OUTGOING,
            state(CallPhase.CONNECTED, listOf(participant("self"))),
            null,
            RingerMode.NORMAL
        )
        assertEquals(RingMode.Silent, mode)
    }
}
