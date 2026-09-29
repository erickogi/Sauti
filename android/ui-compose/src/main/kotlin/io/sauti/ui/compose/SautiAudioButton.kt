package io.sauti.ui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.sauti.android.audio.AudioDevice
import io.sauti.ui.compose.overlay.BluetoothGlyph
import io.sauti.ui.compose.overlay.EarpieceGlyph
import io.sauti.ui.compose.overlay.HeadsetGlyph
import io.sauti.ui.compose.overlay.SpeakerGlyph

internal fun audioButtonActive(device: AudioDevice): Boolean = device != AudioDevice.EARPIECE

@Composable
fun SautiAudioButton(
    uiState: SautiCallUiState,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp
) {
    val strings = LocalSautiStrings.current
    val colors = SautiTheme.colors
    val device = uiState.currentDevice
    val active = audioButtonActive(device)
    var expanded by remember { mutableStateOf(false) }
    val options = audioDeviceOrder(uiState.availableDevices)
    Box(modifier = modifier) {
        SautiIconButton(
            contentDescription = strings.audioOutput,
            backgroundColor = if (active) colors.controlActiveBackground else colors.controlIdleBackground,
            contentColor = if (active) colors.controlActiveContent else colors.controlIdleContent,
            onClick = { expanded = true },
            size = size,
            glyph = { c, m ->
                when (device) {
                    AudioDevice.SPEAKER -> SpeakerGlyph(c, m)
                    AudioDevice.EARPIECE -> EarpieceGlyph(c, m)
                    AudioDevice.BLUETOOTH -> BluetoothGlyph(c, m)
                    AudioDevice.WIRED_HEADSET -> HeadsetGlyph(c, m)
                }
            }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(text = audioDeviceLabel(option, strings)) },
                    onClick = {
                        expanded = false
                        uiState.selectDevice(option)
                    }
                )
            }
        }
    }
}
