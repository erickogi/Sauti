package io.sauti.ui.compose

import io.sauti.android.audio.AudioDevice
import io.sauti.engine.CallPhase
import io.sauti.engine.CallState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AudioOutputTest {

    private class CapturingController : SautiCallController {
        val selected = mutableListOf<AudioDevice>()
        override fun setMuted(muted: Boolean) {}
        override fun setHold(onHold: Boolean) {}
        override fun selectDevice(device: AudioDevice) { selected.add(device) }
        override fun leave() {}
    }

    private fun uiState(
        current: AudioDevice,
        devices: Set<AudioDevice>,
        controller: SautiCallController
    ): SautiCallUiState = buildSautiCallUiState(
        state = CallState(phase = CallPhase.CONNECTED),
        currentDevice = current,
        availableDevices = devices,
        interrupted = false,
        selfParticipantId = null,
        controller = controller
    )

    @Test
    fun routePickerShownWhenBluetoothPresent() {
        assertTrue(showAudioRoutePicker(setOf(AudioDevice.EARPIECE, AudioDevice.SPEAKER, AudioDevice.BLUETOOTH)))
    }

    @Test
    fun routePickerHiddenWhenPhoneOnly() {
        assertFalse(showAudioRoutePicker(setOf(AudioDevice.EARPIECE, AudioDevice.SPEAKER)))
    }

    @Test
    fun routePickerHiddenWhenOnlyWiredHeadset() {
        assertFalse(showAudioRoutePicker(setOf(AudioDevice.SPEAKER, AudioDevice.WIRED_HEADSET)))
        assertFalse(
            showAudioRoutePicker(
                setOf(AudioDevice.EARPIECE, AudioDevice.SPEAKER, AudioDevice.WIRED_HEADSET)
            )
        )
    }

    @Test
    fun routePickerShownWhenBluetoothAndWiredPresent() {
        assertTrue(
            showAudioRoutePicker(
                setOf(
                    AudioDevice.EARPIECE,
                    AudioDevice.SPEAKER,
                    AudioDevice.BLUETOOTH,
                    AudioDevice.WIRED_HEADSET
                )
            )
        )
    }


    @Test
    fun toggleFromSpeakerGoesToEarpieceWhenNoWired() {
        assertEquals(
            AudioDevice.EARPIECE,
            speakerToggleTarget(AudioDevice.SPEAKER, setOf(AudioDevice.EARPIECE, AudioDevice.SPEAKER))
        )
    }

    @Test
    fun toggleFromSpeakerPrefersWiredWhenPresent() {
        assertEquals(
            AudioDevice.WIRED_HEADSET,
            speakerToggleTarget(AudioDevice.SPEAKER, setOf(AudioDevice.SPEAKER, AudioDevice.WIRED_HEADSET))
        )
    }

    @Test
    fun toggleFromEarpieceGoesToSpeaker() {
        assertEquals(
            AudioDevice.SPEAKER,
            speakerToggleTarget(AudioDevice.EARPIECE, setOf(AudioDevice.EARPIECE, AudioDevice.SPEAKER))
        )
    }

    @Test
    fun toggleFromStaleBluetoothGoesToSpeaker() {
        assertEquals(
            AudioDevice.SPEAKER,
            speakerToggleTarget(AudioDevice.BLUETOOTH, setOf(AudioDevice.EARPIECE, AudioDevice.SPEAKER))
        )
    }


    @Test
    fun speakerToggleOnWhenSpeaker() {
        assertTrue(speakerToggleOn(AudioDevice.SPEAKER))
    }

    @Test
    fun speakerToggleOffWhenEarpieceOrWired() {
        assertFalse(speakerToggleOn(AudioDevice.EARPIECE))
        assertFalse(speakerToggleOn(AudioDevice.WIRED_HEADSET))
    }


    @Test
    fun toggleSelectsComputedTarget() {
        val toSpeaker = CapturingController()
        onSpeakerToggle(uiState(AudioDevice.EARPIECE, setOf(AudioDevice.EARPIECE, AudioDevice.SPEAKER), toSpeaker))
        assertEquals(listOf(AudioDevice.SPEAKER), toSpeaker.selected)

        val toEarpiece = CapturingController()
        onSpeakerToggle(uiState(AudioDevice.SPEAKER, setOf(AudioDevice.EARPIECE, AudioDevice.SPEAKER), toEarpiece))
        assertEquals(listOf(AudioDevice.EARPIECE), toEarpiece.selected)

        val toWired = CapturingController()
        onSpeakerToggle(uiState(AudioDevice.SPEAKER, setOf(AudioDevice.SPEAKER, AudioDevice.WIRED_HEADSET), toWired))
        assertEquals(listOf(AudioDevice.WIRED_HEADSET), toWired.selected)
    }

    @Test
    fun toggleNoOpsWhenTargetAbsent() {
        val controller = CapturingController()
        onSpeakerToggle(uiState(AudioDevice.EARPIECE, setOf(AudioDevice.EARPIECE), controller))
        assertTrue(controller.selected.isEmpty())
    }

    @Test
    fun selectRouteDispatchesWhenPresent() {
        val controller = CapturingController()
        onSelectRoute(
            uiState(
                AudioDevice.EARPIECE,
                setOf(AudioDevice.EARPIECE, AudioDevice.SPEAKER, AudioDevice.BLUETOOTH),
                controller
            ),
            AudioDevice.BLUETOOTH
        )
        assertEquals(listOf(AudioDevice.BLUETOOTH), controller.selected)
    }

    @Test
    fun selectRouteNoOpsWhenAbsent() {
        val controller = CapturingController()
        onSelectRoute(
            uiState(AudioDevice.SPEAKER, setOf(AudioDevice.EARPIECE, AudioDevice.SPEAKER), controller),
            AudioDevice.BLUETOOTH
        )
        assertTrue(controller.selected.isEmpty())
    }


    @Test
    fun audioDeviceLabelsAreGeneric() {
        val strings = SautiStrings()
        assertEquals("Earpiece", audioDeviceLabel(AudioDevice.EARPIECE, strings))
        assertEquals("Speaker", audioDeviceLabel(AudioDevice.SPEAKER, strings))
        assertEquals("Bluetooth", audioDeviceLabel(AudioDevice.BLUETOOTH, strings))
        assertEquals("Wired headset", audioDeviceLabel(AudioDevice.WIRED_HEADSET, strings))
    }

    @Test
    fun audioDeviceOrderPlacesBluetoothInEnumPosition() {
        assertEquals(
            listOf(AudioDevice.EARPIECE, AudioDevice.SPEAKER, AudioDevice.BLUETOOTH),
            audioDeviceOrder(setOf(AudioDevice.BLUETOOTH, AudioDevice.EARPIECE, AudioDevice.SPEAKER))
        )
    }
}
