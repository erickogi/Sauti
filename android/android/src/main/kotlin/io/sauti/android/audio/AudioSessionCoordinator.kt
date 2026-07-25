package io.sauti.android.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AudioDevice {
    EARPIECE,
    SPEAKER,
    BLUETOOTH,
    WIRED_HEADSET
}

interface AudioController {
    val currentDevice: StateFlow<AudioDevice>
    val availableDevices: StateFlow<Set<AudioDevice>>
    val interrupted: StateFlow<Boolean>
    var onInterrupted: ((Boolean) -> Unit)?
    fun start()
    fun stop()
    fun selectDevice(device: AudioDevice)
    fun forceInterruption(active: Boolean)
}

class AudioSessionCoordinator(context: Context) : AudioController {

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val currentDeviceState = MutableStateFlow(AudioDevice.EARPIECE)
    override val currentDevice: StateFlow<AudioDevice> get() = currentDeviceState.asStateFlow()

    private val availableDevicesState = MutableStateFlow(availableDevices())
    override val availableDevices: StateFlow<Set<AudioDevice>> get() = availableDevicesState.asStateFlow()

    private val interruptedState = MutableStateFlow(false)
    override val interrupted: StateFlow<Boolean> get() = interruptedState.asStateFlow()

    override var onInterrupted: ((Boolean) -> Unit)? = null

    private var focusRequest: AudioFocusRequest? = null
    private var started = false
    private var deviceCallback: AudioDeviceCallback? = null

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> raiseInterruption()
            AudioManager.AUDIOFOCUS_GAIN -> clearInterruption()
        }
    }

    override fun start() {
        if (started) return
        started = true
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        requestFocus()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) registerDeviceCallback()
        availableDevicesState.value = availableDevices()
        selectDevice(AudioDevice.EARPIECE)
    }

    override fun stop() {
        if (!started) return
        started = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) unregisterDeviceCallback()
        abandonFocus()
        stopLegacyBluetooth()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) clearCommunicationDevice()
        audioManager.mode = AudioManager.MODE_NORMAL
        interruptedState.value = false
    }

    override fun selectDevice(device: AudioDevice) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            routeModern(device)
        } else {
            @Suppress("DEPRECATION")
            routeLegacy(device)
        }
        currentDeviceState.value = device
    }

    fun availableDevices(): Set<AudioDevice> {
        val devices = mutableSetOf(AudioDevice.EARPIECE, AudioDevice.SPEAKER)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val infos = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            for (info in infos) {
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

    private fun requestFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(focusListener)
                .build()
            focusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                focusListener,
                AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
    }

    private fun abandonFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusListener)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun routeModern(device: AudioDevice) {
        val targetType = when (device) {
            AudioDevice.EARPIECE -> AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
            AudioDevice.SPEAKER -> AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            AudioDevice.BLUETOOTH -> AudioDeviceInfo.TYPE_BLUETOOTH_SCO
            AudioDevice.WIRED_HEADSET -> AudioDeviceInfo.TYPE_WIRED_HEADSET
        }
        val match = audioManager.availableCommunicationDevices.firstOrNull { it.type == targetType }
        if (match != null) {
            audioManager.setCommunicationDevice(match)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun clearCommunicationDevice() {
        audioManager.clearCommunicationDevice()
    }

    @Suppress("DEPRECATION")
    private fun routeLegacy(device: AudioDevice) {
        when (device) {
            AudioDevice.SPEAKER -> {
                stopLegacyBluetooth()
                audioManager.isSpeakerphoneOn = true
            }
            AudioDevice.BLUETOOTH -> {
                audioManager.isSpeakerphoneOn = false
                audioManager.isBluetoothScoOn = true
                audioManager.startBluetoothSco()
            }
            AudioDevice.EARPIECE, AudioDevice.WIRED_HEADSET -> {
                stopLegacyBluetooth()
                audioManager.isSpeakerphoneOn = false
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun stopLegacyBluetooth() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            if (audioManager.isBluetoothScoOn) {
                audioManager.isBluetoothScoOn = false
                audioManager.stopBluetoothSco()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun registerDeviceCallback() {
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                reconcileRouting()
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                reconcileRouting()
            }
        }
        deviceCallback = callback
        audioManager.registerAudioDeviceCallback(callback, null)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun unregisterDeviceCallback() {
        deviceCallback?.let { audioManager.unregisterAudioDeviceCallback(it) }
        deviceCallback = null
    }

    private fun reconcileRouting() {
        val available = availableDevices()
        availableDevicesState.value = available
        val preferred = when {
            AudioDevice.WIRED_HEADSET in available -> AudioDevice.WIRED_HEADSET
            AudioDevice.BLUETOOTH in available -> AudioDevice.BLUETOOTH
            else -> currentDeviceState.value
        }
        selectDevice(preferred)
    }

    private fun raiseInterruption() {
        if (interruptedState.value) return
        interruptedState.value = true
        onInterrupted?.invoke(true)
    }

    private fun clearInterruption() {
        if (!interruptedState.value) return
        interruptedState.value = false
        onInterrupted?.invoke(false)
    }

    override fun forceInterruption(active: Boolean) {
        if (active) raiseInterruption() else clearInterruption()
    }
}
