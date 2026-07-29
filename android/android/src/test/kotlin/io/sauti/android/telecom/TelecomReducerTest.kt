package io.sauti.android.telecom

import io.sauti.engine.CallPhase
import io.sauti.engine.CallState
import kotlin.test.Test
import kotlin.test.assertEquals

class TelecomReducerTest {

    private fun state(phase: CallPhase, onHold: Boolean = false): CallState =
        CallState(phase = phase, localOnHold = onHold)

    @Test
    fun idleMapsToRinging() {
        assertEquals(TelecomAction.SetRinging, TelecomReducer.reduce(state(CallPhase.IDLE), null))
    }

    @Test
    fun connectingMapsToDialing() {
        assertEquals(TelecomAction.SetDialing, TelecomReducer.reduce(state(CallPhase.CONNECTING), null))
    }

    @Test
    fun connectedMapsToActive() {
        assertEquals(TelecomAction.SetActive, TelecomReducer.reduce(state(CallPhase.CONNECTED), null))
    }

    @Test
    fun connectedOnHoldMapsToHeld() {
        assertEquals(
            TelecomAction.SetHeld,
            TelecomReducer.reduce(state(CallPhase.CONNECTED, onHold = true), null)
        )
    }

    @Test
    fun leftMapsToDisconnected() {
        assertEquals(TelecomAction.SetDisconnected, TelecomReducer.reduce(state(CallPhase.LEFT), null))
    }

    @Test
    fun dialingToActiveToHeldToDisconnectedProgression() {
        assertEquals(TelecomAction.SetDialing, TelecomReducer.reduce(state(CallPhase.CONNECTING), null))
        assertEquals(TelecomAction.SetActive, TelecomReducer.reduce(state(CallPhase.CONNECTED), null))
        assertEquals(
            TelecomAction.SetHeld,
            TelecomReducer.reduce(state(CallPhase.CONNECTED, onHold = true), null)
        )
        assertEquals(TelecomAction.SetActive, TelecomReducer.reduce(state(CallPhase.CONNECTED), null))
        assertEquals(TelecomAction.SetDisconnected, TelecomReducer.reduce(state(CallPhase.LEFT), null))
    }

    @Test
    fun audioRouteChangedMapsToSetAudioRoute() {
        for (device in TelecomAudioDevice.values()) {
            assertEquals(
                TelecomAction.SetAudioRoute(device),
                TelecomReducer.reduce(state(CallPhase.CONNECTED), TelecomInput.AudioRouteChanged(device))
            )
        }
    }

    @Test
    fun audioRouteChangedIgnoresPhaseWhenNotTerminal() {
        assertEquals(
            TelecomAction.SetAudioRoute(TelecomAudioDevice.Bluetooth),
            TelecomReducer.reduce(
                state(CallPhase.CONNECTING),
                TelecomInput.AudioRouteChanged(TelecomAudioDevice.Bluetooth)
            )
        )
    }

    @Test
    fun disconnectRaceWinsOverAudioRoute() {
        assertEquals(
            TelecomAction.SetDisconnected,
            TelecomReducer.reduce(
                state(CallPhase.LEFT),
                TelecomInput.AudioRouteChanged(TelecomAudioDevice.Speaker)
            )
        )
    }

    @Test
    fun disconnectRaceWinsOverHoldState() {
        assertEquals(
            TelecomAction.SetDisconnected,
            TelecomReducer.reduce(state(CallPhase.LEFT, onHold = true), null)
        )
    }

    @Test
    fun answerInputMapsToAnswerIntent() {
        assertEquals(EngineIntent.Answer, TelecomReducer.reduceUserAction(TelecomInput.Answer))
    }

    @Test
    fun rejectInputMapsToRejectIntent() {
        assertEquals(EngineIntent.Reject, TelecomReducer.reduceUserAction(TelecomInput.Reject))
    }

    @Test
    fun holdInputMapsToHoldIntent() {
        assertEquals(EngineIntent.Hold, TelecomReducer.reduceUserAction(TelecomInput.Hold))
    }

    @Test
    fun unholdInputMapsToUnholdIntent() {
        assertEquals(EngineIntent.Unhold, TelecomReducer.reduceUserAction(TelecomInput.Unhold))
    }

    @Test
    fun disconnectInputMapsToLeaveIntent() {
        assertEquals(EngineIntent.Leave, TelecomReducer.reduceUserAction(TelecomInput.Disconnect))
    }

    @Test
    fun audioRouteInputMapsToSelectDeviceIntent() {
        for (device in TelecomAudioDevice.values()) {
            assertEquals(
                EngineIntent.SelectDevice(device),
                TelecomReducer.reduceUserAction(TelecomInput.AudioRouteChanged(device))
            )
        }
    }
}
