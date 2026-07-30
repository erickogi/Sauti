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
    val returnToCall: String = "Return to call"
)

val LocalSautiStrings = compositionLocalOf { SautiStrings() }
