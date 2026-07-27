package io.sauti.android.ring

import io.sauti.engine.CallPhase
import io.sauti.engine.CallState

object RingReducer {

    fun reduce(
        role: CallRole,
        state: CallState,
        selfParticipantId: String?,
        ringerMode: RingerMode
    ): RingMode = when (role) {
        CallRole.OUTGOING -> reduceOutgoing(state, selfParticipantId)
        CallRole.INCOMING -> reduceIncoming(state, ringerMode)
    }

    private fun reduceOutgoing(state: CallState, selfParticipantId: String?): RingMode = when {
        state.phase == CallPhase.CONNECTING -> RingMode.Ringback
        state.phase == CallPhase.CONNECTED && !remotePresent(state, selfParticipantId) -> RingMode.Ringback
        else -> RingMode.Silent
    }

    private fun reduceIncoming(state: CallState, ringerMode: RingerMode): RingMode = when (state.phase) {
        CallPhase.IDLE, CallPhase.CONNECTING -> incomingFor(ringerMode)
        else -> RingMode.Silent
    }

    private fun incomingFor(ringerMode: RingerMode): RingMode = when (ringerMode) {
        RingerMode.SILENT -> RingMode.Silent
        RingerMode.VIBRATE -> RingMode.Incoming(sound = false)
        RingerMode.NORMAL -> RingMode.Incoming(sound = true)
    }

    private fun remotePresent(state: CallState, selfParticipantId: String?): Boolean =
        state.participants.any { selfParticipantId == null || it.participantId != selfParticipantId }
}
