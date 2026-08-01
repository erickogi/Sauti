package io.sauti.ui.compose

internal enum class SautiHostPhase {
    INCOMING,
    CONNECTING,
    IN_CALL,
    ENDED
}

internal fun resolveHostPhase(
    accepted: Boolean,
    sawSession: Boolean,
    hasSession: Boolean,
    connected: Boolean,
    ended: Boolean
): SautiHostPhase = when {
    sawSession && !hasSession && ended -> SautiHostPhase.ENDED
    hasSession && connected -> SautiHostPhase.IN_CALL
    hasSession -> SautiHostPhase.CONNECTING
    accepted -> SautiHostPhase.CONNECTING
    else -> SautiHostPhase.INCOMING
}
