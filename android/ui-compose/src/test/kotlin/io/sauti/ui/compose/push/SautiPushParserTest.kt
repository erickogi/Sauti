package io.sauti.ui.compose.push

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SautiPushParserTest {

    @Test
    fun parsesIncomingFromNestedPayload() {
        val data = mapOf(
            "event_name" to "voip-call-incoming",
            "payload" to """{"callId":"c1","roomId":"r1","callerName":"Ada","plane":"trip"}"""
        )
        val command = SautiPushParser.parse(data)
        assertTrue(command is SautiPushCommand.Incoming)
        val call = command.call
        assertEquals("c1", call.callId)
        assertEquals("r1", call.roomId)
        assertEquals("Ada", call.callerName)
        assertEquals(mapOf("plane" to "trip"), call.metadata)
    }

    @Test
    fun parsesCancelled() {
        val data = mapOf(
            "event_name" to "voip-call-cancelled",
            "payload" to """{"callId":"c2"}"""
        )
        assertEquals(SautiPushCommand.Cancelled("c2"), SautiPushParser.parse(data))
    }

    @Test
    fun parsesDeclined() {
        val data = mapOf(
            "event_name" to "voip-call-declined",
            "payload" to """{"callId":"c3"}"""
        )
        assertEquals(SautiPushCommand.Declined("c3"), SautiPushParser.parse(data))
    }

    @Test
    fun ignoresUnknownEvent() {
        val data = mapOf(
            "event_name" to "wallet-topup",
            "payload" to """{"callId":"c4"}"""
        )
        assertNull(SautiPushParser.parse(data))
    }

    @Test
    fun ignoresMissingEventKey() {
        val data = mapOf("payload" to """{"callId":"c5"}""")
        assertNull(SautiPushParser.parse(data))
    }

    @Test
    fun ignoresIncomingWithBlankCallId() {
        val data = mapOf(
            "event_name" to "voip-call-incoming",
            "payload" to """{"callId":"","roomId":"r"}"""
        )
        assertNull(SautiPushParser.parse(data))
    }

    @Test
    fun ignoresMalformedPayload() {
        val data = mapOf(
            "event_name" to "voip-call-incoming",
            "payload" to "not-json"
        )
        assertNull(SautiPushParser.parse(data))
    }

    @Test
    fun readsFlatFieldsWhenNoPayload() {
        val data = mapOf(
            "event_name" to "voip-call-incoming",
            "callId" to "c6",
            "roomId" to "r6",
            "callerName" to "Grace",
            "plane" to "trip"
        )
        val command = SautiPushParser.parse(data)
        assertTrue(command is SautiPushCommand.Incoming)
        val call = command.call
        assertEquals("c6", call.callId)
        assertEquals("r6", call.roomId)
        assertEquals("Grace", call.callerName)
        assertEquals(mapOf("plane" to "trip"), call.metadata)
    }

    @Test
    fun blankCallerNameBecomesNull() {
        val data = mapOf(
            "event_name" to "voip-call-incoming",
            "payload" to """{"callId":"c7","callerName":""}"""
        )
        val call = (SautiPushParser.parse(data) as SautiPushCommand.Incoming).call
        assertNull(call.callerName)
    }

    @Test
    fun metadataExcludesCallFieldsAndCarriesExtras() {
        val data = mapOf(
            "event_name" to "voip-call-incoming",
            "payload" to """{"callId":"c8","roomId":"r8","callerName":"X","plane":"trip","name":"Trip 9"}"""
        )
        val call = (SautiPushParser.parse(data) as SautiPushCommand.Incoming).call
        assertEquals(mapOf("plane" to "trip", "name" to "Trip 9"), call.metadata)
    }

    @Test
    fun honoursCustomKeys() {
        val keys = SautiPushKeys(
            eventKey = "type",
            payloadKey = "body",
            incomingEvent = "call.ring"
        )
        val data = mapOf(
            "type" to "call.ring",
            "body" to """{"callId":"c9","roomId":"r9"}"""
        )
        val command = SautiPushParser.parse(data, keys)
        assertTrue(command is SautiPushCommand.Incoming)
        assertEquals("c9", command.call.callId)
    }
}
