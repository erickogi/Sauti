package io.sauti.android.telecom

import io.sauti.engine.CallPhase
import io.sauti.engine.CallState

object TelecomReducer {

    fun reduce(engineState: CallState, input: TelecomInput?): TelecomAction {
        if (engineState.phase == CallPhase.LEFT) {
            return TelecomAction.SetDisconnected
        }
        if (input is TelecomInput.AudioRouteChanged) {
            return TelecomAction.SetAudioRoute(input.device)
        }
        return when (engineState.phase) {
            CallPhase.IDLE -> TelecomAction.SetRinging
            CallPhase.CONNECTING -> TelecomAction.SetDialing
            CallPhase.CONNECTED ->
                if (engineState.localOnHold) TelecomAction.SetHeld else TelecomAction.SetActive
            CallPhase.LEFT -> TelecomAction.SetDisconnected
        }
    }

    fun reduceUserAction(input: TelecomInput): EngineIntent = when (input) {
        TelecomInput.Answer -> EngineIntent.Answer
        TelecomInput.Reject -> EngineIntent.Reject
        TelecomInput.Hold -> EngineIntent.Hold
        TelecomInput.Unhold -> EngineIntent.Unhold
        TelecomInput.Disconnect -> EngineIntent.Leave
        is TelecomInput.AudioRouteChanged -> EngineIntent.SelectDevice(input.device)
    }
}
