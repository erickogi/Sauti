package io.sauti.android.telecom

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import io.sauti.android.audio.AudioController
import io.sauti.android.audio.AudioDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TelecomAudioController(context: Context) : AudioController {

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val currentDeviceState = MutableStateFlow(AudioDevice.EARPIECE)
    override val currentDevice: StateFlow<AudioDevice> get() = currentDeviceState.asStateFlow()

    private val availableDevicesState = MutableStateFlow(queryDevices())
    override val availableDevices: StateFlow<Set<AudioDevice>> get() = availableDevicesState.asStateFlow()

    private val interruptedState = MutableStateFlow(false)
    override val interrupted: StateFlow<Boolean> get() = interruptedState.asStateFlow()

    override var onInterrupted: ((Boolean) -> Unit)? = null

    override fun start() {
        availableDevicesState.value = queryDevices()
    }

    override fun stop() {
        interruptedState.value = false
    }

    override fun selectDevice(device: AudioDevice) {
        currentDeviceState.value = device
    }

    override fun forceInterruption(active: Boolean) {
        if (interruptedState.value == active) return
        interruptedState.value = active
        onInterrupted?.invoke(active)
    }

    private fun queryDevices(): Set<AudioDevice> {
        val devices = mutableSetOf(AudioDevice.EARPIECE, AudioDevice.SPEAKER)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (info in audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                when (info.type) {
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> devices.add(AudioDevice.BLUETOOTH)
                    AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> devices.add(AudioDevice.WIRED_HEADSET)
                }
            }
        }
        return devices
    }
}
