package io.sauti.ui.compose.push

import io.sauti.android.incoming.SautiIncomingCall
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import org.json.JSONObject

data class SautiPushKeys(
    val eventKey: String = "event_name",
    val payloadKey: String = "payload",
    val callIdKey: String = "callId",
    val roomIdKey: String = "roomId",
    val callerNameKey: String = "callerName",
    val incomingEvent: String = "voip-call-incoming",
    val cancelledEvent: String = "voip-call-cancelled",
    val declinedEvent: String = "voip-call-declined"
)

sealed interface SautiPushCommand {
    data class Incoming(val call: SautiIncomingCall) : SautiPushCommand
    data class Cancelled(val callId: String) : SautiPushCommand
    data class Declined(val callId: String) : SautiPushCommand
}

object SautiPushParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parse(data: Map<String, String>, keys: SautiPushKeys = SautiPushKeys()): SautiPushCommand? {
        val event = data[keys.eventKey]?.takeIf { it.isNotBlank() } ?: return null
        if (event != keys.incomingEvent && event != keys.cancelledEvent && event != keys.declinedEvent) {
            return null
        }
        val fields = readFields(data, keys)
        val callId = fields[keys.callIdKey]?.takeIf { it.isNotBlank() } ?: return null
        return when (event) {
            keys.incomingEvent -> SautiPushCommand.Incoming(
                SautiIncomingCall(
                    callId = callId,
                    roomId = fields[keys.roomIdKey].orEmpty(),
                    callerName = fields[keys.callerNameKey]?.takeIf { it.isNotBlank() },
                    metadata = fields.filterKeys {
                        it != keys.callIdKey && it != keys.roomIdKey && it != keys.callerNameKey
                    }
                )
            )
            keys.cancelledEvent -> SautiPushCommand.Cancelled(callId)
            keys.declinedEvent -> SautiPushCommand.Declined(callId)
            else -> null
        }
    }

    private fun readFields(data: Map<String, String>, keys: SautiPushKeys): Map<String, String> {
        val payload = data[keys.payloadKey]
        if (payload.isNullOrBlank()) {
            return data.filterKeys { it != keys.eventKey && it != keys.payloadKey }
        }
        return runCatching {
            json.parseToJsonElement(payload).jsonObject.mapNotNull { (key, element) ->
                (element as? JsonPrimitive)?.contentOrNull?.let { key to it }
            }.toMap()
        }.getOrDefault(emptyMap())
    }
}

object SautiPushBuilder {

    fun build(command: SautiPushCommand, keys: SautiPushKeys = SautiPushKeys()): Map<String, String> =
        mapOf(keys.eventKey to eventName(command, keys), keys.payloadKey to payload(command, keys))

    fun encode(command: SautiPushCommand, keys: SautiPushKeys = SautiPushKeys()): String =
        JSONObject()
            .put(keys.eventKey, eventName(command, keys))
            .put(keys.payloadKey, payload(command, keys))
            .toString()

    private fun eventName(command: SautiPushCommand, keys: SautiPushKeys): String = when (command) {
        is SautiPushCommand.Incoming -> keys.incomingEvent
        is SautiPushCommand.Cancelled -> keys.cancelledEvent
        is SautiPushCommand.Declined -> keys.declinedEvent
    }

    private fun payload(command: SautiPushCommand, keys: SautiPushKeys): String {
        val payload = JSONObject()
        when (command) {
            is SautiPushCommand.Incoming -> {
                payload.put(keys.callIdKey, command.call.callId)
                payload.put(keys.roomIdKey, command.call.roomId)
                command.call.callerName?.let { payload.put(keys.callerNameKey, it) }
                command.call.metadata.forEach { (key, value) ->
                    if (key != keys.callIdKey && key != keys.roomIdKey && key != keys.callerNameKey) {
                        payload.put(key, value)
                    }
                }
            }
            is SautiPushCommand.Cancelled -> payload.put(keys.callIdKey, command.callId)
            is SautiPushCommand.Declined -> payload.put(keys.callIdKey, command.callId)
        }
        return payload.toString()
    }
}
