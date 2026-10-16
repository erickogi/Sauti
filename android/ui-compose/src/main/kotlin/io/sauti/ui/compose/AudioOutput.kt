package io.sauti.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.sauti.android.audio.AudioDevice
import io.sauti.ui.compose.overlay.BluetoothGlyph
import io.sauti.ui.compose.overlay.EarpieceGlyph
import io.sauti.ui.compose.overlay.HeadsetGlyph
import io.sauti.ui.compose.overlay.SpeakerGlyph
import kotlinx.coroutines.launch

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AudioRouteSheet(
    devices: Set<AudioDevice>,
    current: AudioDevice,
    onSelect: (AudioDevice) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalSautiStrings.current
    val colors = SautiTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    fun dismissAnimated() {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) onDismiss()
        }
    }

    LaunchedEffect(devices) {
        if (!showAudioRoutePicker(devices)) dismissAnimated()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                text = strings.audioRouteSheetTitle,
                style = SautiTheme.typography.label,
                color = colors.onSurface,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
            )
            audioDeviceOrder(devices).forEach { device ->
                val selected = device == current
                val content = if (selected) colors.controlActiveContent else colors.onSurface
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .selectable(
                            selected = selected,
                            role = Role.RadioButton,
                            onClick = {
                                onSelect(device)
                                dismissAnimated()
                            }
                        )
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    AudioDeviceGlyph(device, content, Modifier.size(24.dp))
                    Text(
                        text = audioDeviceLabel(device, strings),
                        style = SautiTheme.typography.label,
                        color = content,
                        modifier = Modifier.weight(1f)
                    )
                    RadioButton(
                        selected = selected,
                        onClick = null,
                        colors = RadioButtonDefaults.colors(
                            selectedColor = colors.controlActiveContent,
                            unselectedColor = colors.onSurfaceMuted
                        )
                    )
                }
            }
        }
    }
}
