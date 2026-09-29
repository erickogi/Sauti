package io.sauti.ui.compose

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf

@Immutable
data class SautiStrings(
    val you: String = "You",
    val participant: String = "Participant",
    val mute: String = "Mute",
    val unmute: String = "Unmute",
    val muted: String = "Muted",
    val end: String = "End",
    val connecting: String = "Connecting",
    val reconnecting: String = "Reconnecting…",
    val inCall: String = "In call",
    val waitingForPeer: String = "Waiting for someone to join",
    val ended: String = "Ended",
    val audioOutput: String = "Audio output",
    val speaker: String = "Speaker",
    val earpiece: String = "Earpiece",
    val bluetooth: String = "Bluetooth",
    val wiredHeadset: String = "Wired headset",
    val qualityGood: String = "Good",
    val qualityFair: String = "Fair",
    val qualityPoor: String = "Poor",
    val interrupted: String = "Paused for a phone call",
    val onHold: String = "On hold",
    val incomingCallTitle: String = "Incoming call",
    val accept: String = "Accept",
    val decline: String = "Decline",
    val returnToCall: String = "Return to call",
    val minimize: String = "Minimize",
    val overlayPromptTitle: String = "Keep this call handy",
    val overlayPromptBody: String =
        "To keep talking while you use other screens, allow a small call bubble to " +
            "show over other apps. You only need to turn this on once.",
    val overlayPromptConfirm: String = "Turn on",
    val overlayPromptDismiss: String = "Not now",
    val callBarReturn: String = "Tap to return to call",
    val slideToAnswer: String = "Slide to answer"
)

val LocalSautiStrings = compositionLocalOf { SautiStrings() }
