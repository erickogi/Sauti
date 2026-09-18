package io.sauti.ui.compose

import io.sauti.android.incoming.SautiIncomingCall
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class SautiTokenProviderTest {

    private val ticket = SautiSessionTicket(
        url = "wss://x",
        token = "t",
        roomId = "r",
        participantId = "p",
        displayTitle = "Call",
        callId = "c1"
    )

    private val outgoing = SautiTokenRequest.Outgoing("target", emptyMap())

    @Test
    fun providerIsAuthoritativeAndLegacyNotInvoked() = runTest {
        var legacyCalled = false
        val result = resolveTicket(SautiTokenProvider { ticket }, outgoing) {
            legacyCalled = true
            null
        }
        assertEquals(ticket, result)
        assertFalse(legacyCalled)
    }

    @Test
    fun legacyUsedWhenNoProvider() = runTest {
        val result = resolveTicket(null, outgoing) { ticket }
        assertEquals(ticket, result)
    }

    @Test
    fun providerNullResultFailsClosedWithoutFallback() = runTest {
        var legacyCalled = false
        val result = resolveTicket(SautiTokenProvider { null }, outgoing) {
            legacyCalled = true
            ticket
        }
        assertNull(result)
        assertFalse(legacyCalled)
    }

    @Test
    fun providerThrowPropagatesForCallerRunCatching() = runTest {
        assertFailsWith<IllegalStateException> {
            resolveTicket(SautiTokenProvider { error("boom") }, outgoing) { ticket }
        }
    }

    @Test
    fun acceptRequestCarriesIncomingMetadata() = runTest {
        val call = SautiIncomingCall("c9", "r9", "Ada", mapOf("plane" to "trip"))
        var seen: Map<String, String>? = null
        resolveTicket(SautiTokenProvider { req -> seen = req.metadata; ticket }, SautiTokenRequest.Accept(call)) {
            ticket
        }
        assertEquals(mapOf("plane" to "trip"), seen)
    }
}
