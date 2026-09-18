package io.sauti.ui.compose.push

import io.sauti.android.incoming.SautiIncomingCall
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

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
