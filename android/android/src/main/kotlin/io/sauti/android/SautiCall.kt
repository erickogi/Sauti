package io.sauti.android

import io.sauti.android.audio.AudioDevice
import io.sauti.engine.CallEvent
import io.sauti.engine.CallState
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface SautiCall {
    val state: StateFlow<CallState>
    val events: SharedFlow<CallEvent>
    val currentDevice: StateFlow<AudioDevice>
    val availableDevices: StateFlow<Set<AudioDevice>>
    val interrupted: StateFlow<Boolean>
    val selfParticipantId: String?

    fun setMuted(muted: Boolean)
    fun setHold(onHold: Boolean)
    fun selectDevice(device: AudioDevice)
    fun hangUp()
}
