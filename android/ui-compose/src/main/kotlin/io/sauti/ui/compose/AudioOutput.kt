package io.sauti.ui.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.sauti.android.audio.AudioDevice
import io.sauti.ui.compose.overlay.BluetoothGlyph
import io.sauti.ui.compose.overlay.EarpieceGlyph
import io.sauti.ui.compose.overlay.HeadsetGlyph
import io.sauti.ui.compose.overlay.SpeakerGlyph

internal fun showAudioRoutePicker(devices: Set<AudioDevice>): Boolean =
    devices.contains(AudioDevice.BLUETOOTH)

internal fun speakerToggleOn(current: AudioDevice): Boolean =
    current == AudioDevice.SPEAKER

internal fun speakerToggleTarget(current: AudioDevice, devices: Set<AudioDevice>): AudioDevice =
    if (current == AudioDevice.SPEAKER) {
        if (devices.contains(AudioDevice.WIRED_HEADSET)) AudioDevice.WIRED_HEADSET
        else AudioDevice.EARPIECE
    } else {
        AudioDevice.SPEAKER
    }

internal fun onSpeakerToggle(uiState: SautiCallUiState) {
    val target = speakerToggleTarget(uiState.currentDevice, uiState.availableDevices)
    if (uiState.availableDevices.contains(target)) uiState.selectDevice(target)
}

internal fun onSelectRoute(uiState: SautiCallUiState, device: AudioDevice) {
    if (uiState.availableDevices.contains(device)) uiState.selectDevice(device)
}

@Composable
internal fun SautiControlCaption(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = SautiTheme.typography.caption,
        color = SautiTheme.colors.onSurfaceMuted,
        maxLines = 1,
        modifier = modifier
    )
}

@Composable
internal fun AudioDeviceGlyph(device: AudioDevice, color: Color, modifier: Modifier = Modifier) {
    when (device) {
        AudioDevice.SPEAKER -> SpeakerGlyph(color, modifier)
        AudioDevice.EARPIECE -> EarpieceGlyph(color, modifier)
        AudioDevice.BLUETOOTH -> BluetoothGlyph(color, modifier)
        AudioDevice.WIRED_HEADSET -> HeadsetGlyph(color, modifier)
    }
}

@Composable
internal fun CheckGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val path = Path().apply {
            moveTo(size.width * 0.2f, size.height * 0.55f)
            lineTo(size.width * 0.42f, size.height * 0.78f)
            lineTo(size.width * 0.82f, size.height * 0.26f)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = size.minDimension * 0.14f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

@Composable
internal fun AudioRouteMenu(
    expanded: Boolean,
    devices: Set<AudioDevice>,
    current: AudioDevice,
    onSelect: (AudioDevice) -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalSautiStrings.current
    val colors = SautiTheme.colors
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        audioDeviceOrder(devices).forEach { device ->
            val selected = device == current
            val content = if (selected) colors.controlActiveContent else colors.onSurface
            DropdownMenuItem(
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier
                            .heightIn(min = 56.dp)
                            .padding(end = 8.dp)
                            .semantics { this.selected = selected }
                    ) {
                        AudioDeviceGlyph(device, content, Modifier.size(22.dp))
                        Text(text = audioDeviceLabel(device, strings), color = content)
                        if (selected) {
                            CheckGlyph(colors.controlActiveContent, Modifier.size(18.dp))
                        }
                    }
                },
                onClick = {
                    onSelect(device)
                    onDismiss()
                }
            )
        }
    }
}
