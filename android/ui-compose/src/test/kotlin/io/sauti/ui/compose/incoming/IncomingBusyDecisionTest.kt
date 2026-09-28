package io.sauti.ui.compose.incoming

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IncomingBusyDecisionTest {

    @Test
    fun sameCallIdIsNotBusy() {
        assertFalse(
            IncomingBusyDecision.declineBusy(
                accepted = true,
                outgoing = true,
                hasActiveSession = true,
                newCallId = "call-1",
                currentCallId = "call-1"
            )
        )
    }

    @Test
    fun unacceptedWithNoSessionIsNotBusy() {
        assertFalse(
            IncomingBusyDecision.declineBusy(
                accepted = false,
                outgoing = false,
                hasActiveSession = false,
                newCallId = "call-2",
                currentCallId = "call-1"
            )
        )
    }

    @Test
    fun acceptedIsBusy() {
        assertTrue(
            IncomingBusyDecision.declineBusy(
                accepted = true,
                outgoing = false,
                hasActiveSession = false,
                newCallId = "call-2",
                currentCallId = "call-1"
            )
        )
    }

    @Test
    fun outgoingIsBusy() {
        assertTrue(
            IncomingBusyDecision.declineBusy(
                accepted = false,
                outgoing = true,
                hasActiveSession = false,
                newCallId = "call-2",
                currentCallId = "call-1"
            )
        )
    }

    @Test
    fun activeSessionIsBusy() {
        assertTrue(
            IncomingBusyDecision.declineBusy(
                accepted = false,
                outgoing = false,
                hasActiveSession = true,
                newCallId = "call-2",
                currentCallId = "call-1"
            )
        )
    }
}
