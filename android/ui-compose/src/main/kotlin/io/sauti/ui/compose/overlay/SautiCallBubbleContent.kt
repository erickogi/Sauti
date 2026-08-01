package io.sauti.ui.compose.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.sauti.android.overlay.BubbleExpansion
import io.sauti.android.overlay.ContrastTone
import io.sauti.android.overlay.contrastToneOn
import io.sauti.engine.CallPhase
import io.sauti.ui.compose.LocalSautiStrings
import io.sauti.ui.compose.SautiCallUiState
import io.sauti.ui.compose.SautiTheme
import io.sauti.ui.compose.formatDuration
import kotlin.math.roundToInt

private val CollapsedSize = 56.dp
private val FillSize = 46.dp
private val RingWidth = 1.5.dp
private val Elevation = 6.dp
private val ControlSize = 44.dp
private val GlyphSize = 22.dp

@Composable
fun SautiCallBubbleContent(
    uiState: SautiCallUiState,
    expansion: BubbleExpansion,
    onReturn: () -> Unit,
    onToggleMute: () -> Unit,
    onEnd: () -> Unit,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (expansion) {
        BubbleExpansion.Collapsed -> CollapsedHandle(uiState = uiState, modifier = modifier)
        BubbleExpansion.Expanded -> ExpandedControls(
            uiState = uiState,
            onReturn = onReturn,
            onToggleMute = onToggleMute,
            onEnd = onEnd,
            onCollapse = onCollapse,
            modifier = modifier
        )
    }
}

@Composable
private fun CollapsedHandle(uiState: SautiCallUiState, modifier: Modifier) {
    val initial = uiState.others.firstOrNull()?.label?.trim()?.firstOrNull()?.uppercaseChar()
    val accent = SautiTheme.colors.accent
    val contentColor = toneColor(toneOn(accent))
    val ringColor = toneColor(toneOn(SautiTheme.colors.surface))
    Box(
        modifier = modifier.size(CollapsedSize),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .size(FillSize)
                .shadow(Elevation, CircleShape)
                .clip(CircleShape)
                .background(accent)
                .border(RingWidth, ringColor, CircleShape),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (initial != null) {
                Text(
                    text = initial.toString(),
                    style = SautiTheme.typography.status,
                    color = contentColor
                )
            } else {
                PhoneGlyph(color = contentColor, modifier = Modifier.size(GlyphSize))
            }
            if (uiState.phase == CallPhase.CONNECTED) {
                Text(
                    text = formatDuration(uiState.durationMs),
                    style = SautiTheme.typography.caption,
                    color = contentColor
                )
            }
        }
    }
}

private fun toneOn(color: Color): ContrastTone =
    contrastToneOn(
        (color.red * 255f).roundToInt(),
        (color.green * 255f).roundToInt(),
        (color.blue * 255f).roundToInt()
    )

private fun toneColor(tone: ContrastTone): Color =
    if (tone == ContrastTone.Light) Color.White else Color(0xFF1B1B1B)

@Composable
private fun ExpandedControls(
    uiState: SautiCallUiState,
    onReturn: () -> Unit,
    onToggleMute: () -> Unit,
    onEnd: () -> Unit,
    onCollapse: () -> Unit,
    modifier: Modifier
) {
    val strings = LocalSautiStrings.current
    val muted = uiState.localMuted
    val onAccent = SautiTheme.colors.onDanger
    Row(
        modifier = modifier
            .clip(SautiTheme.shapes.control)
            .background(SautiTheme.colors.surface)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlyphButton(
            contentDescription = strings.returnToCall,
            background = SautiTheme.colors.accent,
            onClick = {
                onCollapse()
                onReturn()
            }
        ) {
            ReturnChevronGlyph(color = onAccent, modifier = Modifier.size(GlyphSize))
        }
        GlyphButton(
            contentDescription = if (muted) strings.unmute else strings.mute,
            background = SautiTheme.colors.accent,
            onClick = onToggleMute
        ) {
            if (muted) {
                MicOffGlyph(color = onAccent, modifier = Modifier.size(GlyphSize))
            } else {
                MicGlyph(color = onAccent, modifier = Modifier.size(GlyphSize))
            }
        }
        GlyphButton(
            contentDescription = strings.end,
            background = SautiTheme.colors.danger,
            onClick = onEnd
        ) {
            CallEndGlyph(color = SautiTheme.colors.onDanger, modifier = Modifier.size(GlyphSize))
        }
    }
}

@Composable
private fun GlyphButton(
    contentDescription: String,
    background: Color,
    onClick: () -> Unit,
    glyph: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(ControlSize)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        glyph()
    }
}
