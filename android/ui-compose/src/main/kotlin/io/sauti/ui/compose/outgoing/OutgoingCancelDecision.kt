package io.sauti.ui.compose.outgoing

internal object OutgoingCancelDecision {

    fun cancelOnSessionGone(peerJoined: Boolean, tearingDown: Boolean): Boolean =
        !peerJoined && !tearingDown

    fun endOnSessionGone(peerJoined: Boolean, tearingDown: Boolean): Boolean =
        peerJoined && !cancelOnSessionGone(peerJoined, tearingDown)

    fun cancelOnNoAnswer(peerJoined: Boolean, callKnown: Boolean, unconnectedMatch: Boolean): Boolean =
        !peerJoined && callKnown && unconnectedMatch
}
