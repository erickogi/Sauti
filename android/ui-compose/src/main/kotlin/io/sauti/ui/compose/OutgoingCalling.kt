package io.sauti.ui.compose

internal fun connectingShowsCalling(outgoing: Boolean, redesignEnabled: Boolean): Boolean =
    redesignEnabled && outgoing

internal fun outgoingRingbackActive(
    redesignEnabled: Boolean,
    outgoing: Boolean,
    hasSession: Boolean,
    hasOthers: Boolean
): Boolean = redesignEnabled && outgoing && hasSession && !hasOthers

internal fun connectingStatus(
    outgoing: Boolean,
    redesignEnabled: Boolean,
    strings: SautiStrings
): String = if (connectingShowsCalling(outgoing, redesignEnabled)) strings.calling else strings.connecting

internal fun outgoingRingbackStatus(
    redesignEnabled: Boolean,
    outgoing: Boolean,
    hasSession: Boolean,
    hasOthers: Boolean,
    strings: SautiStrings
): String? =
    if (outgoingRingbackActive(redesignEnabled, outgoing, hasSession, hasOthers)) strings.ringing else null

internal fun pulseAnimated(animationScale: Float): Boolean = animationScale > 0f
