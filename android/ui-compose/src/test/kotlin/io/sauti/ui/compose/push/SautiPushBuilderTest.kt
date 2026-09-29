package io.sauti.ui.compose.push

import io.sauti.android.incoming.SautiIncomingCall
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SautiPushBuilderTest {

    @Test
    fun encodeMatchesTheHandRolledOrgJsonMarker() {
        val call = SautiIncomingCall(
            callId = "c1",
            roomId = "r1",
            callerName = "Ada",
            metadata = linkedMapOf("callerAcctId" to "u1", "plane" to "trip")
        )

        val handRolled = JSONObject()
            .put("event_name", "voip-call-incoming")
            .put(
                "payload",
                JSONObject()
                    .put("callId", "c1")
                    .put("roomId", "r1")
                    .put("callerName", "Ada")
                    .put("callerAcctId", "u1")
                    .put("plane", "trip")
                    .toString()
            )
            .toString()

        assertEquals(handRolled, SautiPushBuilder.encode(SautiPushCommand.Incoming(call)))
    }

    @Test
    fun buildRoundTripsThroughParser() {
        val call = SautiIncomingCall(
            callId = "c1",
            roomId = "r1",
            callerName = "Ada",
            metadata = mapOf("callerAcctId" to "u1", "plane" to "trip")
        )

        val parsed = SautiPushParser.parse(SautiPushBuilder.build(SautiPushCommand.Incoming(call)))

        assertTrue(parsed is SautiPushCommand.Incoming)
        assertEquals(call.callId, parsed.call.callId)
        assertEquals(call.roomId, parsed.call.roomId)
        assertEquals(call.callerName, parsed.call.callerName)
        assertEquals(call.metadata, parsed.call.metadata)
    }

    @Test
    fun encodeRoundTripsCallerNameWithSpecialCharacters() {
        val call = SautiIncomingCall(
            callId = "c1",
            roomId = "r1",
            callerName = "José \"JJ\" 😀",
            metadata = mapOf("callerAcctId" to "u1")
        )

        val marker = SautiPushBuilder.encode(SautiPushCommand.Incoming(call))
        val payload = JSONObject(marker).getString("payload")
        val parsed = SautiPushParser.parse(
            mapOf("event_name" to "voip-call-incoming", "payload" to payload)
        )

        assertTrue(parsed is SautiPushCommand.Incoming)
        assertEquals("José \"JJ\" 😀", parsed.call.callerName)
    }

    @Test
    fun cancelledMarkerRoundTrips() {
        val parsed = SautiPushParser.parse(
            SautiPushBuilder.build(SautiPushCommand.Cancelled("c9"))
        )
        assertTrue(parsed is SautiPushCommand.Cancelled)
        assertEquals("c9", parsed.callId)
    }
}
