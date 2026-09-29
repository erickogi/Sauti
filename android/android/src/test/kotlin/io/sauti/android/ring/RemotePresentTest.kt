package io.sauti.android.ring

import io.sauti.engine.CallPhase
import io.sauti.engine.CallState
import io.sauti.engine.ConnectionState
import io.sauti.engine.ParticipantSnapshot
import io.sauti.engine.Quality
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RemotePresentTest {

    private fun participant(id: String): ParticipantSnapshot = ParticipantSnapshot(
        participantId = id,
        metadata = emptyMap(),
        muted = false,
        onHold = false,
        connectionState = ConnectionState.CONNECTED,
        quality = Quality.GOOD
    )

    private fun state(vararg ids: String): CallState =
        CallState(phase = CallPhase.CONNECTED, participants = ids.map(::participant))

    @Test
    fun emptyRoomHasNoRemote() {
        assertFalse(RingReducer.remotePresent(state(), "self"))
    }

    @Test
    fun onlySelfHasNoRemote() {
        assertFalse(RingReducer.remotePresent(state("self"), "self"))
    }

    @Test
    fun aDistinctParticipantIsRemote() {
        assertTrue(RingReducer.remotePresent(state("self", "other"), "self"))
    }

    @Test
    fun nullSelfHasNoRemote() {
        assertFalse(RingReducer.remotePresent(state("self"), null))
    }
}
