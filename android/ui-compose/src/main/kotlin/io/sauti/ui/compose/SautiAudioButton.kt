package io.sauti.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.sauti.android.audio.AudioDevice

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
    val devices = uiState.availableDevices
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (showAudioRoutePicker(devices)) {
            val active = audioButtonActive(device)
            SautiIconButton(
                contentDescription = "${strings.audioOutput}, ${audioDeviceLabel(device, strings)}",
                backgroundColor = if (active) colors.controlActiveBackground else colors.controlIdleBackground,
                contentColor = if (active) colors.controlActiveContent else colors.controlIdleContent,
                onClick = { expanded = true },
                size = size,
                glyph = { c, m -> AudioDeviceGlyph(device, c, m) }
            )
            SautiControlCaption(audioDeviceLabel(device, strings))
        } else {
            val on = speakerToggleOn(device)
            SautiIconButton(
                contentDescription = strings.speaker,
                backgroundColor = if (on) colors.controlActiveBackground else colors.controlIdleBackground,
                contentColor = if (on) colors.controlActiveContent else colors.controlIdleContent,
                onClick = { onSpeakerToggle(uiState) },
                size = size,
                stateDescription = if (on) strings.speakerOn else strings.speakerOff,
                glyph = { c, m -> AudioDeviceGlyph(AudioDevice.SPEAKER, c, m) }
            )
            SautiControlCaption(strings.speaker)
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
