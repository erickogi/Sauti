package io.sauti.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.sauti.engine.CallPhase
import io.sauti.engine.Quality
import java.util.Locale

internal fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / MILLIS_PER_SECOND).coerceAtLeast(0)
    val minutes = totalSeconds / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}

internal fun callStatusText(uiState: SautiCallUiState, strings: SautiStrings): String = when {
    uiState.phase == CallPhase.LEFT -> strings.ended
    uiState.phase == CallPhase.IDLE || uiState.phase == CallPhase.CONNECTING -> strings.connecting
    uiState.reconnecting -> strings.reconnecting
    !uiState.hasOthers -> strings.waitingForPeer
    else -> strings.inCall
}

internal fun qualityText(quality: Quality, strings: SautiStrings): String = when (quality) {
    Quality.GOOD -> strings.qualityGood
    Quality.DEGRADED -> strings.qualityFair
    Quality.POOR -> strings.qualityPoor
}

@Composable
fun DurationTimer(durationMs: Long, modifier: Modifier = Modifier) {
    Text(
        text = formatDuration(durationMs),
        style = SautiTheme.typography.timer,
        color = SautiTheme.colors.onSurface,
        modifier = modifier
    )
}

@Composable
fun CallStatus(uiState: SautiCallUiState, modifier: Modifier = Modifier) {
    val strings = LocalSautiStrings.current
    val text = callStatusText(uiState, strings)
    Text(
        text = text,
        style = SautiTheme.typography.status,
        color = SautiTheme.colors.onSurfaceMuted,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite }
    )
}

@Composable
fun QualityIndicator(quality: Quality, modifier: Modifier = Modifier) {
    val strings = LocalSautiStrings.current
    val label = qualityText(quality, strings)
    Row(
        modifier = modifier.semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(SautiTheme.colors.colorFor(quality))
        ) {}
        Text(
            text = label,
            style = SautiTheme.typography.caption,
            color = SautiTheme.colors.onSurfaceMuted
        )
    }
}

@Composable
fun InterruptionBanner(uiState: SautiCallUiState, modifier: Modifier = Modifier) {
    if (!uiState.interrupted) return
    val strings = LocalSautiStrings.current
    Text(
        text = strings.interrupted,
        style = SautiTheme.typography.caption,
        color = SautiTheme.colors.onDanger,
        modifier = modifier
            .clip(SautiTheme.shapes.surface)
            .background(SautiTheme.colors.danger)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }
    )
}

private const val MILLIS_PER_SECOND = 1000L
private const val SECONDS_PER_MINUTE = 60L
