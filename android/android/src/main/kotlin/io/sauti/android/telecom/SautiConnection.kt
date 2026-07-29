package io.sauti.android.telecom

import android.net.Uri
import android.os.Build
import android.telecom.CallAudioState
import android.telecom.Connection
import android.telecom.DisconnectCause
import android.telecom.TelecomManager
import androidx.annotation.RequiresApi
import io.sauti.engine.CallState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.O)
class SautiConnection(
    scope: CoroutineScope,
    state: StateFlow<CallState>,
    displayTitle: String,
    participantId: String,
    private val onIntent: (EngineIntent) -> Unit
) : Connection() {

    init {
        connectionProperties = PROPERTY_SELF_MANAGED
        audioModeIsVoip = true
        setCallerDisplayName(displayTitle, TelecomManager.PRESENTATION_ALLOWED)
        setAddress(addressOf(participantId), TelecomManager.PRESENTATION_ALLOWED)
        scope.launch {
            state.collect { snapshot -> apply(TelecomReducer.reduce(snapshot, null)) }
        }
    }

    override fun onAnswer() = onIntent(TelecomReducer.reduceUserAction(TelecomInput.Answer))

    override fun onReject() = onIntent(TelecomReducer.reduceUserAction(TelecomInput.Reject))

    override fun onHold() = onIntent(TelecomReducer.reduceUserAction(TelecomInput.Hold))

    override fun onUnhold() = onIntent(TelecomReducer.reduceUserAction(TelecomInput.Unhold))

    override fun onDisconnect() = onIntent(TelecomReducer.reduceUserAction(TelecomInput.Disconnect))

    override fun onCallAudioStateChanged(state: CallAudioState) {
        val device = deviceOf(state.route) ?: return
        onIntent(TelecomReducer.reduceUserAction(TelecomInput.AudioRouteChanged(device)))
    }

    private fun apply(action: TelecomAction) {
        when (action) {
            TelecomAction.SetDialing -> setDialing()
            TelecomAction.SetRinging -> setRinging()
            TelecomAction.SetActive -> setActive()
            TelecomAction.SetHeld -> setOnHold()
            TelecomAction.SetDisconnected -> {
                setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
                destroy()
            }
            is TelecomAction.SetAudioRoute -> Unit
        }
    }

    private fun deviceOf(route: Int): TelecomAudioDevice? = when (route) {
        CallAudioState.ROUTE_EARPIECE -> TelecomAudioDevice.Earpiece
        CallAudioState.ROUTE_SPEAKER -> TelecomAudioDevice.Speaker
        CallAudioState.ROUTE_BLUETOOTH -> TelecomAudioDevice.Bluetooth
        CallAudioState.ROUTE_WIRED_HEADSET -> TelecomAudioDevice.WiredHeadset
        else -> null
    }

    private fun addressOf(participantId: String): Uri = Uri.fromParts(SCHEME, participantId, null)

    companion object {
        const val SCHEME = "sauti"
    }
}
