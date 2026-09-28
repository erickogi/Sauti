package io.sauti.ui.compose.incoming

internal object IncomingBusyDecision {
    fun declineBusy(
        accepted: Boolean,
        outgoing: Boolean,
        hasActiveSession: Boolean,
        newCallId: String,
        currentCallId: String?
    ): Boolean = newCallId != currentCallId && (accepted || outgoing || hasActiveSession)
}
