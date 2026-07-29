package io.sauti.android.proximity

import io.sauti.android.audio.AudioDevice

enum class ScreenIntent {
    Off,
    On
}

object ProximityReducer {
    fun reduce(callActive: Boolean, near: Boolean, audioDevice: AudioDevice): ScreenIntent =
        if (callActive && near && audioDevice == AudioDevice.EARPIECE) {
            ScreenIntent.Off
        } else {
            ScreenIntent.On
        }
}
