package io.sauti.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import io.sauti.android.SautiClient
import io.sauti.android.audio.AudioDevice
import io.sauti.engine.CallPhase
import io.sauti.engine.CallState
import io.sauti.engine.ConnectionState
import io.sauti.engine.ParticipantSnapshot
import io.sauti.engine.Quality
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Immutable
data class SautiParticipantRow(
    val participantId: String,
    val label: String,
    val isSelf: Boolean,
    val muted: Boolean,
    val onHold: Boolean,
    val connectionState: ConnectionState,
    val quality: Quality
)

internal interface SautiCallController {
    fun setMuted(muted: Boolean)
    fun setHold(onHold: Boolean)
    fun selectDevice(device: AudioDevice)
    fun leave()
}

@Stable
class SautiCallUiState internal constructor(
    val phase: CallPhase,
    val roster: List<SautiParticipantRow>,
    val orderedParticipants: List<ParticipantSnapshot>,
    val self: SautiParticipantRow?,
    val others: List<SautiParticipantRow>,
    val localMuted: Boolean,
    val localOnHold: Boolean,
    val durationMs: Long,
    val quality: Quality,
    val reconnecting: Boolean,
    val interrupted: Boolean,
    val currentDevice: AudioDevice,
    val availableDevices: Set<AudioDevice>,
    private val controller: SautiCallController
) {
    val hasOthers: Boolean get() = others.isNotEmpty()
    val isEnded: Boolean get() = phase == CallPhase.LEFT

    fun toggleMute() = controller.setMuted(!localMuted)

    fun setMuted(muted: Boolean) = controller.setMuted(muted)

    fun setHold(onHold: Boolean) = controller.setHold(onHold)

    fun selectDevice(device: AudioDevice) = controller.selectDevice(device)

    fun leave() = controller.leave()
}

internal fun shortParticipantId(participantId: String): String =
    if (participantId.length <= SHORT_ID_LENGTH) participantId else participantId.take(SHORT_ID_LENGTH)

internal fun participantLabel(participant: ParticipantSnapshot): String {
    val name = (participant.metadata["name"] as? JsonPrimitive)?.contentOrNull?.trim()
    return if (!name.isNullOrEmpty()) name else shortParticipantId(participant.participantId)
}

internal fun buildRoster(
    participants: List<ParticipantSnapshot>,
    selfParticipantId: String?
): List<SautiParticipantRow> =
    participants.map { participant ->
        SautiParticipantRow(
            participantId = participant.participantId,
            label = participantLabel(participant),
            isSelf = selfParticipantId != null && participant.participantId == selfParticipantId,
            muted = participant.muted,
            onHold = participant.onHold,
            connectionState = participant.connectionState,
            quality = participant.quality
        )
    }.sortedByDescending { it.isSelf }

internal fun buildSautiCallUiState(
    state: CallState,
    currentDevice: AudioDevice,
    availableDevices: Set<AudioDevice>,
    interrupted: Boolean,
    selfParticipantId: String?,
    controller: SautiCallController
): SautiCallUiState {
    val roster = buildRoster(state.participants, selfParticipantId)
    val ordered = state.participants.sortedByDescending {
        selfParticipantId != null && it.participantId == selfParticipantId
    }
    val self = roster.firstOrNull { it.isSelf }
    val others = roster.filter { !it.isSelf }
    return SautiCallUiState(
        phase = state.phase,
        roster = roster,
        orderedParticipants = ordered,
        self = self,
        others = others,
        localMuted = state.localMuted,
        localOnHold = state.localOnHold,
        durationMs = state.durationMs,
        quality = state.quality,
        reconnecting = state.reconnecting,
        interrupted = interrupted,
        currentDevice = currentDevice,
        availableDevices = availableDevices,
        controller = controller
    )
}

private class ClientController(private val client: SautiClient) : SautiCallController {
    override fun setMuted(muted: Boolean) = client.setMuted(muted)
    override fun setHold(onHold: Boolean) = client.setHold(onHold)
    override fun selectDevice(device: AudioDevice) = client.selectDevice(device)
    override fun leave() = client.leave()
}

@Composable
fun rememberSautiCallUiState(
    client: SautiClient,
    selfParticipantId: String? = null
): SautiCallUiState {
    val state by client.state.collectAsState()
    val currentDevice by client.currentDevice.collectAsState()
    val availableDevices by client.availableDevices.collectAsState()
    val interrupted by client.interrupted.collectAsState()
    val controller = remember(client) { ClientController(client) }
    return remember(state, currentDevice, availableDevices, interrupted, selfParticipantId, controller) {
        buildSautiCallUiState(
            state = state,
            currentDevice = currentDevice,
            availableDevices = availableDevices,
            interrupted = interrupted,
            selfParticipantId = selfParticipantId,
            controller = controller
        )
    }
}

private const val SHORT_ID_LENGTH = 8
