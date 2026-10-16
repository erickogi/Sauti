package io.sauti.ui.compose

import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import io.sauti.android.audio.AudioDevice

internal fun audioDeviceLabel(device: AudioDevice, strings: SautiStrings): String = when (device) {
    AudioDevice.EARPIECE -> strings.earpiece
    AudioDevice.SPEAKER -> strings.speaker
    AudioDevice.BLUETOOTH -> strings.bluetooth
    AudioDevice.WIRED_HEADSET -> strings.wiredHeadset
}

internal fun audioDeviceOrder(devices: Set<AudioDevice>): List<AudioDevice> =
    AudioDevice.entries.filter { devices.contains(it) }

@Composable
fun AudioDevicePicker(uiState: SautiCallUiState, modifier: Modifier = Modifier) {
    val strings = LocalSautiStrings.current
    val device = uiState.currentDevice
    val devices = uiState.availableDevices
    var expanded by remember { mutableStateOf(false) }

    if (showAudioRoutePicker(devices)) {
        val currentLabel = audioDeviceLabel(device, strings)
        FilledTonalButton(
            onClick = { expanded = true },
            shape = SautiTheme.shapes.control,
            modifier = modifier.semantics {
                contentDescription = strings.audioOutput + ", " + currentLabel
            }
        ) {
            Text(text = currentLabel)
        }
    } else {
        val on = speakerToggleOn(device)
        FilledTonalButton(
            onClick = { onSpeakerToggle(uiState) },
            shape = SautiTheme.shapes.control,
            modifier = modifier.semantics {
                stateDescription = if (on) strings.speakerOn else strings.speakerOff
            }
        ) {
            Text(text = strings.speaker)
        }
    }

    if (expanded) {
        AudioRouteSheet(
            devices = devices,
            current = device,
            onSelect = { onSelectRoute(uiState, it) },
            onDismiss = { expanded = false }
        )
    }
}
