package io.sauti.engine

import kotlinx.serialization.json.JsonElement

enum class CallPhase {
    IDLE,
    CONNECTING,
    CONNECTED,
    LEFT
}

data class ParticipantSnapshot(
    val participantId: String,
    val metadata: Map<String, JsonElement>,
    val muted: Boolean,
    val onHold: Boolean,
    val connectionState: ConnectionState,
    val quality: Quality
)

data class CallState(
    val phase: CallPhase = CallPhase.IDLE,
    val participants: List<ParticipantSnapshot> = emptyList(),
    val localMuted: Boolean = false,
    val localOnHold: Boolean = false,
    val durationMs: Long = 0,
    val quality: Quality = Quality.GOOD,
    val reconnecting: Boolean = false
)
