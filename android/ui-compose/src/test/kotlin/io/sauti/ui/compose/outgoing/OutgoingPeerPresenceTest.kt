package io.sauti.ui.compose.outgoing

import io.sauti.android.outgoing.SautiOutgoingCallRegistry
import io.sauti.engine.CallPhase
import io.sauti.engine.ConnectionState
import io.sauti.engine.ParticipantSnapshot
import io.sauti.engine.Quality
import org.junit.After
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OutgoingPeerPresenceTest {

    private fun participant(id: String): ParticipantSnapshot = ParticipantSnapshot(
        participantId = id,
        metadata = emptyMap(),
        muted = false,
        onHold = false,
        connectionState = ConnectionState.CONNECTED,
        quality = Quality.GOOD
    )

    @After
    fun tearDown() {
        SautiOutgoingCallRegistry.clear()
    }

    @Test
    fun connectedAloneCallerIsNotAPeer() {
        assertFalse(OutgoingPeerPresence.peerJoined(CallPhase.CONNECTED, listOf(participant("p1"))))
    }

    @Test
    fun aSecondParticipantIsAJoinedPeer() {
        assertTrue(
            OutgoingPeerPresence.peerJoined(
                CallPhase.CONNECTED,
                listOf(participant("p1"), participant("p2"))
            )
        )
    }

    @Test
    fun connectingIsNeverAJoinedPeer() {
        assertFalse(
            OutgoingPeerPresence.peerJoined(
                CallPhase.CONNECTING,
                listOf(participant("p1"), participant("p2"))
            )
        )
    }

    @Test
    fun connectedAloneCallerLeavesTheRegistryUnconnectedSoDeclineIsHonored() {
        SautiOutgoingCallRegistry.begin("c1")

        if (OutgoingPeerPresence.peerJoined(CallPhase.CONNECTED, listOf(participant("p1")))) {
            SautiOutgoingCallRegistry.markConnected()
        }

        assertTrue(SautiOutgoingCallRegistry.matchesUnconnected("c1"))
    }

    @Test
    fun connectedPeerMarksTheRegistryConnected() {
        SautiOutgoingCallRegistry.begin("c1")

        if (OutgoingPeerPresence.peerJoined(CallPhase.CONNECTED, listOf(participant("p1"), participant("p2")))) {
            SautiOutgoingCallRegistry.markConnected()
        }

        assertFalse(SautiOutgoingCallRegistry.matchesUnconnected("c1"))
    }
}
