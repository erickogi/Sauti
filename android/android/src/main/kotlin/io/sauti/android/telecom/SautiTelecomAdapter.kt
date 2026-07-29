package io.sauti.android.telecom

import android.os.Build
import androidx.annotation.RequiresApi
import io.sauti.android.SautiClient
import io.sauti.android.audio.AudioDevice
import kotlinx.coroutines.CoroutineScope

@RequiresApi(Build.VERSION_CODES.O)
class SautiTelecomAdapter(
    private val scope: CoroutineScope,
    private val client: SautiClient,
    private val onAnswer: () -> Unit = {}
) {

    fun attach(displayTitle: String, participantId: String) {
        SautiConnectionService.provider = SautiConnectionProvider {
            SautiConnection(scope, client.state, displayTitle, participantId, ::onIntent)
        }
    }

    fun detach() {
        SautiConnectionService.provider = null
    }

    private fun onIntent(intent: EngineIntent) {
        when (intent) {
            EngineIntent.Answer -> onAnswer()
            EngineIntent.Reject -> client.leave()
            EngineIntent.Hold -> client.setHold(true)
            EngineIntent.Unhold -> client.setHold(false)
            EngineIntent.Leave -> client.leave()
            is EngineIntent.SelectDevice -> client.selectDevice(deviceOf(intent.device))
        }
    }

    private fun deviceOf(device: TelecomAudioDevice): AudioDevice = when (device) {
        TelecomAudioDevice.Earpiece -> AudioDevice.EARPIECE
        TelecomAudioDevice.Speaker -> AudioDevice.SPEAKER
        TelecomAudioDevice.Bluetooth -> AudioDevice.BLUETOOTH
        TelecomAudioDevice.WiredHeadset -> AudioDevice.WIRED_HEADSET
    }
}
