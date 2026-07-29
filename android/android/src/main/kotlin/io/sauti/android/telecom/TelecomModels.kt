package io.sauti.android.telecom

enum class TelecomAudioDevice {
    Earpiece,
    Speaker,
    Bluetooth,
    WiredHeadset
}

sealed interface CallMediaState {
    data object Dialing : CallMediaState
    data object Ringing : CallMediaState
    data object Active : CallMediaState
    data object Held : CallMediaState
    data object Disconnected : CallMediaState
}

sealed interface TelecomInput {
    data object Answer : TelecomInput
    data object Reject : TelecomInput
    data object Hold : TelecomInput
    data object Unhold : TelecomInput
    data object Disconnect : TelecomInput
    data class AudioRouteChanged(val device: TelecomAudioDevice) : TelecomInput
}

sealed interface TelecomAction {
    data object SetDialing : TelecomAction
    data object SetRinging : TelecomAction
    data object SetActive : TelecomAction
    data object SetHeld : TelecomAction
    data object SetDisconnected : TelecomAction
    data class SetAudioRoute(val device: TelecomAudioDevice) : TelecomAction
}

sealed interface EngineIntent {
    data object Answer : EngineIntent
    data object Reject : EngineIntent
    data object Hold : EngineIntent
    data object Unhold : EngineIntent
    data object Leave : EngineIntent
    data class SelectDevice(val device: TelecomAudioDevice) : EngineIntent
}
