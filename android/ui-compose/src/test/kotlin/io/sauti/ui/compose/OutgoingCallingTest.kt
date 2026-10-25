package io.sauti.ui.compose

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OutgoingCallingTest {

    private val strings = SautiStrings()

    @Test
    fun callingShownForOutgoingWhenEnabled() {
        assertTrue(connectingShowsCalling(outgoing = true, redesignEnabled = true))
    }

    @Test
    fun callingNotShownForAcceptedIncoming() {
        assertFalse(connectingShowsCalling(outgoing = false, redesignEnabled = true))
    }

    @Test
    fun callingNotShownWhenFlagOff() {
        assertFalse(connectingShowsCalling(outgoing = true, redesignEnabled = false))
    }

    @Test
    fun connectingStatusIsCallingForOutgoingRedesign() {
        assertEquals(strings.calling, connectingStatus(outgoing = true, redesignEnabled = true, strings = strings))
    }

    @Test
    fun connectingStatusIsConnectingForAcceptedIncoming() {
        assertEquals(strings.connecting, connectingStatus(outgoing = false, redesignEnabled = true, strings = strings))
    }

    @Test
    fun connectingStatusIsConnectingWhenFlagOff() {
        assertEquals(strings.connecting, connectingStatus(outgoing = true, redesignEnabled = false, strings = strings))
    }

    @Test
    fun ringbackActiveForOutgoingWithSessionAndNoPeer() {
        assertTrue(outgoingRingbackActive(redesignEnabled = true, outgoing = true, hasSession = true, hasOthers = false))
    }

    @Test
    fun ringbackGoneOncePeerJoins() {
        assertFalse(outgoingRingbackActive(redesignEnabled = true, outgoing = true, hasSession = true, hasOthers = true))
    }

    @Test
    fun ringbackInactiveWithoutSession() {
        assertFalse(outgoingRingbackActive(redesignEnabled = true, outgoing = true, hasSession = false, hasOthers = false))
    }

    @Test
    fun ringbackInactiveForIncoming() {
        assertFalse(outgoingRingbackActive(redesignEnabled = true, outgoing = false, hasSession = true, hasOthers = false))
    }

    @Test
    fun ringbackInactiveWhenFlagOff() {
        assertFalse(outgoingRingbackActive(redesignEnabled = false, outgoing = true, hasSession = true, hasOthers = false))
    }

    @Test
    fun ringbackStatusIsRingingThenFlipsToNoLabelOnJoin() {
        assertEquals(
            strings.ringing,
            outgoingRingbackStatus(
                redesignEnabled = true,
                outgoing = true,
                hasSession = true,
                hasOthers = false,
                strings = strings
            )
        )
        assertNull(
            outgoingRingbackStatus(
                redesignEnabled = true,
                outgoing = true,
                hasSession = true,
                hasOthers = true,
                strings = strings
            )
        )
    }

    @Test
    fun ringbackStatusHasNoLabelForIncoming() {
        assertNull(
            outgoingRingbackStatus(
                redesignEnabled = true,
                outgoing = false,
                hasSession = true,
                hasOthers = false,
                strings = strings
            )
        )
    }

    @Test
    fun pulseIsSuppressedWhenAnimationsDisabled() {
        assertFalse(pulseAnimated(0f))
    }

    @Test
    fun pulseAnimatesWhenAnimationScalePositive() {
        assertTrue(pulseAnimated(1f))
        assertTrue(pulseAnimated(0.5f))
    }

    @Test
    fun flagOffRestoresOldLabelsEverywhere() {
        assertFalse(connectingShowsCalling(outgoing = true, redesignEnabled = false))
        assertEquals(strings.connecting, connectingStatus(outgoing = true, redesignEnabled = false, strings = strings))
        assertFalse(outgoingRingbackActive(redesignEnabled = false, outgoing = true, hasSession = true, hasOthers = false))
        assertNull(
            outgoingRingbackStatus(
                redesignEnabled = false,
                outgoing = true,
                hasSession = true,
                hasOthers = false,
                strings = strings
            )
        )
    }
}
