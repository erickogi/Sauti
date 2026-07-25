package io.sauti.ui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
    var expanded by remember { mutableStateOf(false) }
    val options = audioDeviceOrder(uiState.availableDevices)
    val currentLabel = audioDeviceLabel(uiState.currentDevice, strings)
    val description = strings.audioOutput + ", " + currentLabel
    Box(modifier = modifier) {
        FilledTonalButton(
            onClick = { expanded = true },
            shape = SautiTheme.shapes.control,
            modifier = Modifier.semantics { contentDescription = description }
        ) {
            Text(text = currentLabel)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { device ->
                val label = audioDeviceLabel(device, strings)
                DropdownMenuItem(
                    text = { Text(text = label) },
                    onClick = {
                        expanded = false
                        uiState.selectDevice(device)
                    }
                )
            }
        }
    }
}
