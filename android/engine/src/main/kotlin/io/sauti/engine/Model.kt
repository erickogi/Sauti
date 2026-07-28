package io.sauti.engine

import kotlinx.serialization.json.JsonElement

enum class ConnectionState {
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    UNREACHABLE,
    LEFT
}

enum class Quality {
    GOOD,
    DEGRADED,
    POOR
}

data class ParticipantState(
    val muted: Boolean = false,
    val onHold: Boolean = false
)

data class Participant(
    val participantId: String,
    val joinedAt: Long,
    val metadata: Map<String, JsonElement>,
    val connectionState: ConnectionState,
    val state: ParticipantState
)

data class RoomInfo(
    val roomId: String,
    val startedAt: Long?,
    val maxParticipants: Int
)

data class PartialState(
    val muted: Boolean? = null,
    val onHold: Boolean? = null
)

data class IceServer(
    val urls: List<String>,
    val username: String? = null,
    val credential: String? = null
)

data class SdpDescription(
    val type: String,
    val sdp: String
)

data class IceCandidate(
    val candidate: String,
    val sdpMid: String?,
    val sdpMLineIndex: Int?
)

data class StatsSample(
    val rttMs: Double,
    val loss: Double,
    val jitterMs: Double,
    val audioLevel: Double? = null,
    val jitterBufferMs: Double? = null,
    val fractionLost: Double? = null
)

data class QoeSample(
    val participantId: String,
    val rttMs: Double,
    val loss: Double,
    val jitterMs: Double,
    val audioLevel: Double? = null,
    val jitterBufferMs: Double? = null,
    val fractionLost: Double? = null,
    val sampledAt: Long
)
