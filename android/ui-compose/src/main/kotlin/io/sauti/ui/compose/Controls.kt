package io.sauti.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.sauti.ui.compose.overlay.CallEndGlyph
import io.sauti.ui.compose.overlay.MicGlyph
import io.sauti.ui.compose.overlay.MicOffGlyph

@Composable
fun MuteButton(uiState: SautiCallUiState, modifier: Modifier = Modifier) {
    val strings = LocalSautiStrings.current
    val muted = uiState.localMuted
    val actionLabel = if (muted) strings.unmute else strings.mute
    val state = if (muted) strings.muted else strings.mute
    FilledTonalButton(
        onClick = { uiState.toggleMute() },
        modifier = modifier.semantics { stateDescription = state },
        shape = SautiTheme.shapes.control
    ) {
        Text(text = actionLabel)
    }
}

@Composable
fun EndCallButton(
    modifier: Modifier = Modifier,
    onEnd: () -> Unit
) {
    val strings = LocalSautiStrings.current
    Button(
        onClick = onEnd,
        modifier = modifier,
        shape = SautiTheme.shapes.control,
        colors = ButtonDefaults.buttonColors(
            containerColor = SautiTheme.colors.danger,
            contentColor = SautiTheme.colors.onDanger
        )
    ) {
        Text(text = strings.end)
    }
}

@Composable
fun SautiMuteButton(
    uiState: SautiCallUiState,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp
) {
    val strings = LocalSautiStrings.current
    val muted = uiState.localMuted
    val colors = SautiTheme.colors
    SautiIconButton(
        contentDescription = if (muted) strings.unmute else strings.mute,
        backgroundColor = if (muted) colors.controlActiveBackground else colors.controlIdleBackground,
        contentColor = if (muted) colors.controlActiveContent else colors.controlIdleContent,
        onClick = { uiState.toggleMute() },
        modifier = modifier,
        size = size,
        glyph = { c, m -> if (muted) MicOffGlyph(c, m) else MicGlyph(c, m) }
    )
}

@Composable
fun SautiEndCallButton(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    onEnd: () -> Unit
) {
    val strings = LocalSautiStrings.current
    SautiIconButton(
        contentDescription = strings.end,
        backgroundColor = SautiTheme.colors.danger,
        contentColor = SautiTheme.colors.onDanger,
        onClick = onEnd,
        modifier = modifier,
        size = size,
        glyph = { c, m -> CallEndGlyph(c, m) }
    )
}

@Composable
fun ControlBar(
    uiState: SautiCallUiState,
    modifier: Modifier = Modifier,
    onEnd: () -> Unit = { uiState.leave() }
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MuteButton(uiState = uiState)
        AudioDevicePicker(uiState = uiState)
        EndCallButton(onEnd = onEnd)
    }
}
