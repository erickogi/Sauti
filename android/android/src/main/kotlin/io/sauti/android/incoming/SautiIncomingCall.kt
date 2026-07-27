package io.sauti.android.incoming

import android.content.Intent
import android.os.Bundle

const val EXTRA_CALL_ID = "io.sauti.android.incoming.CALL_ID"
const val EXTRA_ROOM_ID = "io.sauti.android.incoming.ROOM_ID"
const val EXTRA_CALLER_NAME = "io.sauti.android.incoming.CALLER_NAME"
const val EXTRA_METADATA = "io.sauti.android.incoming.METADATA"

data class SautiIncomingCall(
    val callId: String,
    val roomId: String,
    val callerName: String? = null,
    val metadata: Map<String, String> = emptyMap()
) {

    fun toExtras(intent: Intent): Intent {
        intent.putExtra(EXTRA_CALL_ID, callId)
        intent.putExtra(EXTRA_ROOM_ID, roomId)
        intent.putExtra(EXTRA_CALLER_NAME, callerName)
        if (metadata.isNotEmpty()) {
            val nested = Bundle()
            for ((key, value) in metadata) {
                nested.putString(key, value)
            }
            intent.putExtra(EXTRA_METADATA, nested)
        }
        return intent
    }

    companion object {
        fun fromExtras(bundle: Bundle?): SautiIncomingCall? {
            if (bundle == null) return null
            val callId = bundle.getString(EXTRA_CALL_ID).orEmpty()
            if (callId.isBlank()) return null
            val roomId = bundle.getString(EXTRA_ROOM_ID).orEmpty()
            val callerName = bundle.getString(EXTRA_CALLER_NAME)
            val nested = bundle.getBundle(EXTRA_METADATA)
            val metadata = if (nested == null) {
                emptyMap()
            } else {
                buildMap {
                    for (key in nested.keySet()) {
                        val value = nested.getString(key)
                        if (value != null) put(key, value)
                    }
                }
            }
            return SautiIncomingCall(callId, roomId, callerName, metadata)
        }
    }
}
