package io.sauti.android.incoming

import android.content.Intent
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SautiIncomingCallPayloadTest {

    @Test
    fun roundTripsAllFieldsIncludingMetadata() {
        val call = SautiIncomingCall(
            callId = "call-1",
            roomId = "room-1",
            callerName = "Ada",
            metadata = mapOf("name" to "Ada Lovelace", "avatar" to "url")
        )
        val intent = call.toExtras(Intent())

        val decoded = SautiIncomingCall.fromExtras(intent.extras)

        assertEquals(call, decoded)
    }

    @Test
    fun roundTripsWithNullCallerNameAndEmptyMetadata() {
        val call = SautiIncomingCall(callId = "call-2", roomId = "room-2")
        val intent = call.toExtras(Intent())

        val decoded = SautiIncomingCall.fromExtras(intent.extras)

        assertEquals(call, decoded)
        assertNull(decoded?.callerName)
        assertEquals(emptyMap(), decoded?.metadata)
    }

    @Test
    fun blankCallIdDecodesToNull() {
        val call = SautiIncomingCall(callId = "", roomId = "room-3")
        val intent = call.toExtras(Intent())

        assertNull(SautiIncomingCall.fromExtras(intent.extras))
    }

    @Test
    fun nullBundleDecodesToNull() {
        assertNull(SautiIncomingCall.fromExtras(null))
    }
}
