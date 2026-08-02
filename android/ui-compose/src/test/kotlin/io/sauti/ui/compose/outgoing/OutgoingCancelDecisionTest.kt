package io.sauti.ui.compose.outgoing

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OutgoingCancelDecisionTest {

    @Test
    fun unconnectedTeardownCancels() {
        assertTrue(OutgoingCancelDecision.cancelOnSessionGone(peerJoined = false, tearingDown = false))
    }

    @Test
    fun connectedTeardownDoesNotCancel() {
        assertFalse(OutgoingCancelDecision.cancelOnSessionGone(peerJoined = true, tearingDown = false))
    }

    @Test
    fun explicitTeardownDoesNotDoubleCancel() {
        assertFalse(OutgoingCancelDecision.cancelOnSessionGone(peerJoined = false, tearingDown = true))
    }

    @Test
    fun connectedAndTearingDownDoesNotCancel() {
        assertFalse(OutgoingCancelDecision.cancelOnSessionGone(peerJoined = true, tearingDown = true))
    }

    @Test
    fun noAnswerCancelsWhenUnconnectedAndKnown() {
        assertTrue(OutgoingCancelDecision.cancelOnNoAnswer(peerJoined = false, callKnown = true, unconnectedMatch = true))
    }

    @Test
    fun noAnswerDoesNotCancelWhenPeerJoined() {
        assertFalse(OutgoingCancelDecision.cancelOnNoAnswer(peerJoined = true, callKnown = true, unconnectedMatch = true))
    }

    @Test
    fun noAnswerDoesNotCancelWhenCallUnknown() {
        assertFalse(OutgoingCancelDecision.cancelOnNoAnswer(peerJoined = false, callKnown = false, unconnectedMatch = false))
    }

    @Test
    fun noAnswerDoesNotCancelWhenNotAnUnconnectedMatch() {
        assertFalse(OutgoingCancelDecision.cancelOnNoAnswer(peerJoined = false, callKnown = true, unconnectedMatch = false))
    }
}
