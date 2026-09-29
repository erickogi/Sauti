package io.sauti.ui.compose

internal enum class SautiHostPhase {
    INCOMING,
    CONNECTING,
    IN_CALL,
    ENDED
}

internal enum class SautiHostScreen {
    INCOMING,
    CONNECTING,
    SOLO,
    ENDED
}

internal fun resolveHostScreen(
    phase: SautiHostPhase,
    hasSession: Boolean,
    hasIncoming: Boolean
): SautiHostScreen = when (phase) {
    SautiHostPhase.INCOMING -> SautiHostScreen.INCOMING
    SautiHostPhase.IN_CALL -> SautiHostScreen.SOLO
    SautiHostPhase.CONNECTING -> if (hasSession && !hasIncoming) SautiHostScreen.SOLO else SautiHostScreen.CONNECTING
    SautiHostPhase.ENDED -> SautiHostScreen.ENDED
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
