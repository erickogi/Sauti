package io.sauti.ui.compose

import org.junit.Test
import kotlin.test.assertEquals

class SautiHostPhaseTest {

    @Test
    fun freshIncomingResolvesToIncoming() {
        assertEquals(
            SautiHostPhase.INCOMING,
            resolveHostPhase(
                accepted = false,
                sawSession = false,
                hasSession = false,
                connected = false,
                ended = false
            )
        )
    }

    @Test
    fun freshIncomingWithStaleEndedStaysIncoming() {
        assertEquals(
            SautiHostPhase.INCOMING,
            resolveHostPhase(
                accepted = false,
                sawSession = false,
                hasSession = false,
                connected = false,
                ended = true
            )
        )
    }

    @Test
    fun acceptedBeforeSessionResolvesToConnecting() {
        assertEquals(
            SautiHostPhase.CONNECTING,
            resolveHostPhase(
                accepted = true,
                sawSession = false,
                hasSession = false,
                connected = false,
                ended = false
            )
        )
    }

    @Test
    fun acceptedWithStaleEndedButNoSessionStaysConnecting() {
        assertEquals(
            SautiHostPhase.CONNECTING,
            resolveHostPhase(
                accepted = true,
                sawSession = false,
                hasSession = false,
                connected = false,
                ended = true
            )
        )
    }

    @Test
    fun sessionPresentNotConnectedResolvesToConnecting() {
        assertEquals(
            SautiHostPhase.CONNECTING,
            resolveHostPhase(
                accepted = true,
                sawSession = true,
                hasSession = true,
                connected = false,
                ended = false
            )
        )
    }

    @Test
    fun sessionConnectedResolvesToInCall() {
        assertEquals(
            SautiHostPhase.IN_CALL,
            resolveHostPhase(
                accepted = true,
                sawSession = true,
                hasSession = true,
                connected = true,
                ended = false
            )
        )
    }

    @Test
    fun connectedThenLeftResolvesToEnded() {
        assertEquals(
            SautiHostPhase.ENDED,
            resolveHostPhase(
                accepted = true,
                sawSession = true,
                hasSession = false,
                connected = false,
                ended = true
            )
        )
    }

    @Test
    fun leftBeforeSessionEverSeenIsNotEnded() {
        assertEquals(
            SautiHostPhase.CONNECTING,
            resolveHostPhase(
                accepted = true,
                sawSession = false,
                hasSession = false,
                connected = false,
                ended = true
            )
        )
    }

    @Test
    fun glareLeftBeforeSessionWithoutAcceptStaysIncoming() {
        assertEquals(
            SautiHostPhase.INCOMING,
            resolveHostPhase(
                accepted = false,
                sawSession = false,
                hasSession = false,
                connected = false,
                ended = true
            )
        )
    }

    @Test
    fun liveSessionWinsOverStaleEnded() {
        assertEquals(
            SautiHostPhase.IN_CALL,
            resolveHostPhase(
                accepted = true,
                sawSession = true,
                hasSession = true,
                connected = true,
                ended = true
            )
        )
    }

    @Test
    fun liveSessionPreConnectedWinsOverStaleEnded() {
        assertEquals(
            SautiHostPhase.CONNECTING,
            resolveHostPhase(
                accepted = true,
                sawSession = true,
                hasSession = true,
                connected = false,
                ended = true
            )
        )
    }

    @Test
    fun endedRequiresSawSessionLatch() {
        assertEquals(
            SautiHostPhase.CONNECTING,
            resolveHostPhase(
                accepted = true,
                sawSession = false,
                hasSession = false,
                connected = false,
                ended = true
            )
        )
        assertEquals(
            SautiHostPhase.ENDED,
            resolveHostPhase(
                accepted = true,
                sawSession = true,
                hasSession = false,
                connected = false,
                ended = true
            )
        )
    }

    @Test
    fun outgoingSessionWhileConnectingRendersSolo() {
        assertEquals(
            SautiHostScreen.SOLO,
            resolveHostScreen(SautiHostPhase.CONNECTING, hasSession = true, hasIncoming = false)
        )
    }

    @Test
    fun incomingSessionWhileConnectingRendersConnecting() {
        assertEquals(
            SautiHostScreen.CONNECTING,
            resolveHostScreen(SautiHostPhase.CONNECTING, hasSession = true, hasIncoming = true)
        )
    }

    @Test
    fun incomingWithoutSessionRendersConnecting() {
        assertEquals(
            SautiHostScreen.CONNECTING,
            resolveHostScreen(SautiHostPhase.CONNECTING, hasSession = false, hasIncoming = true)
        )
    }

    @Test
    fun acceptedWithoutSessionRendersConnecting() {
        assertEquals(
            SautiHostScreen.CONNECTING,
            resolveHostScreen(SautiHostPhase.CONNECTING, hasSession = false, hasIncoming = false)
        )
    }

    @Test
    fun inCallRendersSolo() {
        assertEquals(
            SautiHostScreen.SOLO,
            resolveHostScreen(SautiHostPhase.IN_CALL, hasSession = true, hasIncoming = false)
        )
    }

    @Test
    fun incomingRendersIncoming() {
        assertEquals(
            SautiHostScreen.INCOMING,
            resolveHostScreen(SautiHostPhase.INCOMING, hasSession = false, hasIncoming = true)
        )
    }

    @Test
    fun endedRendersEnded() {
        assertEquals(
            SautiHostScreen.ENDED,
            resolveHostScreen(SautiHostPhase.ENDED, hasSession = false, hasIncoming = false)
        )
    }

    @Test
    fun acceptToConnectedToLeftFullOrdering() {
        assertEquals(
            SautiHostPhase.INCOMING,
            resolveHostPhase(false, false, false, false, false)
        )
        assertEquals(
            SautiHostPhase.CONNECTING,
            resolveHostPhase(true, false, false, false, false)
        )
        assertEquals(
            SautiHostPhase.CONNECTING,
            resolveHostPhase(true, true, true, false, false)
        )
        assertEquals(
            SautiHostPhase.IN_CALL,
            resolveHostPhase(true, true, true, true, false)
        )
        assertEquals(
            SautiHostPhase.ENDED,
            resolveHostPhase(true, true, false, false, true)
        )
    }
}
