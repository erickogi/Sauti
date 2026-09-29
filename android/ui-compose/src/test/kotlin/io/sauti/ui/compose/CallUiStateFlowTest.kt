package io.sauti.ui.compose

import io.sauti.android.audio.AudioDevice
import io.sauti.engine.CallPhase
import io.sauti.engine.CallState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CallUiStateFlowTest {

    private fun controller() = object : SautiCallController {
        override fun setMuted(muted: Boolean) {}
        override fun setHold(onHold: Boolean) {}
        override fun selectDevice(device: AudioDevice) {}
        override fun leave() {}
    }

    @Test
    fun combinesSourcesIntoUiState() = runTest {
        val state = MutableStateFlow(
            CallState(
                phase = CallPhase.CONNECTED,
                participants = emptyList(),
                localMuted = true
            )
        )
        val device = MutableStateFlow(AudioDevice.SPEAKER)
        val devices = MutableStateFlow(setOf(AudioDevice.EARPIECE, AudioDevice.SPEAKER))
        val interrupted = MutableStateFlow(true)

        val ui = callUiStateFlow(state, device, devices, interrupted, null, controller()).first()

        assertEquals(CallPhase.CONNECTED, ui.phase)
        assertTrue(ui.localMuted)
        assertEquals(AudioDevice.SPEAKER, ui.currentDevice)
        assertEquals(setOf(AudioDevice.EARPIECE, AudioDevice.SPEAKER), ui.availableDevices)
        assertTrue(ui.interrupted)
    }

    @Test
    fun emitsWhenAnySourceChanges() = runTest {
        val state = MutableStateFlow(CallState(phase = CallPhase.CONNECTING))
        val device = MutableStateFlow(AudioDevice.EARPIECE)
        val devices = MutableStateFlow(setOf(AudioDevice.EARPIECE))
        val interrupted = MutableStateFlow(false)

        val initial = callUiStateFlow(state, device, devices, interrupted, "me", controller()).first()
        assertEquals(CallPhase.CONNECTING, initial.phase)

        state.value = CallState(phase = CallPhase.CONNECTED)
        val next = callUiStateFlow(state, device, devices, interrupted, "me", controller()).first()
        assertEquals(CallPhase.CONNECTED, next.phase)
    }
}
