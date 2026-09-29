package io.sauti.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.sauti.ui.compose.overlay.MicOffGlyph
import io.sauti.ui.compose.overlay.MinimizeGlyph

@Composable
fun SautiSoloCallScreen(
    uiState: SautiCallUiState,
    modifier: Modifier = Modifier,
    onEnd: (() -> Unit)? = null,
    onMinimize: (() -> Unit)? = null
) {
    val strings = LocalSautiStrings.current
    val title = uiState.others.firstOrNull()?.label
    val peerMuted = uiState.others.any { it.muted }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SautiTheme.colors.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.padding(top = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (title != null) {
                    SautiAvatar(name = title)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = SautiTheme.colors.onSurface
                        )
                        if (peerMuted) {
                            MicOffGlyph(
                                color = SautiTheme.colors.onSurfaceMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                CallStatus(uiState = uiState)
                Text(
                    text = formatDuration(uiState.durationMs),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = SautiTheme.colors.onSurface
                )
                QualityIndicator(quality = uiState.quality)
                InterruptionBanner(uiState = uiState)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SautiMuteButton(uiState = uiState)
                SautiAudioButton(uiState = uiState)
                SautiEndCallButton(onEnd = onEnd ?: { uiState.leave() })
            }
        }
        if (onMinimize != null) {
            SautiIconButton(
                contentDescription = strings.minimize,
                backgroundColor = SautiTheme.colors.controlIdleBackground,
                contentColor = SautiTheme.colors.controlIdleContent,
                onClick = onMinimize,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
                size = 44.dp,
                glyph = { c, m -> MinimizeGlyph(c, m) }
            )
        }
    }
}
