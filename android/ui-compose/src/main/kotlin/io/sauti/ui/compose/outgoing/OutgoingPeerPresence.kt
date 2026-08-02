package io.sauti.ui.compose.outgoing

import io.sauti.engine.CallPhase
import io.sauti.engine.ParticipantSnapshot

internal object OutgoingPeerPresence {

    fun peerJoined(phase: CallPhase, participants: List<ParticipantSnapshot>): Boolean =
        phase == CallPhase.CONNECTED && participants.size >= 2
}
