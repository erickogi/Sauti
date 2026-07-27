package io.sauti.ui.compose.incoming

import io.sauti.android.incoming.SautiIncomingCall
import io.sauti.ui.compose.SautiStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SautiIncomingCallHostTest {

    @Test
    fun currentReturnsConfiguredCallbacksAndTheme() {
        val accepted = mutableListOf<SautiIncomingCall>()
        val declined = mutableListOf<SautiIncomingCall>()
        val strings = SautiStrings(accept = "Answer", decline = "Reject")
        val config = SautiIncomingCallHost.Config(
            onAccept = { accepted.add(it) },
            onDecline = { declined.add(it) },
            strings = strings
        )

        SautiIncomingCallHost.configure(config)
        val current = SautiIncomingCallHost.current()

        assertSame(strings, current.strings)
        val call = SautiIncomingCall("call-1", "room-1")
        current.onAccept(call)
        current.onDecline(call)
        assertEquals(listOf(call), accepted)
        assertEquals(listOf(call), declined)
    }

    @Test
    fun configureReplacesPreviousConfig() {
        val first = SautiIncomingCallHost.Config(onAccept = {}, onDecline = {}, strings = SautiStrings(accept = "One"))
        val second = SautiIncomingCallHost.Config(onAccept = {}, onDecline = {}, strings = SautiStrings(accept = "Two"))

        SautiIncomingCallHost.configure(first)
        SautiIncomingCallHost.configure(second)

        assertEquals("Two", SautiIncomingCallHost.current().strings.accept)
    }

    @Test
    fun defaultCallbacksDoNotThrow() {
        SautiIncomingCallHost.configure(SautiIncomingCallHost.Config(onAccept = {}, onDecline = {}))
        val current = SautiIncomingCallHost.current()
        val call = SautiIncomingCall("call-2", "room-2")

        current.onAccept(call)
        current.onDecline(call)

        assertTrue(current.colors == null)
    }
}
