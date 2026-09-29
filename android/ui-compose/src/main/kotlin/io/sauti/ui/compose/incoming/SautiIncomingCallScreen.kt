package io.sauti.ui.compose.incoming

import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.sauti.ui.compose.SautiAvatar
import io.sauti.ui.compose.SautiColors
import io.sauti.ui.compose.SautiIconButton
import io.sauti.ui.compose.SautiStrings
import io.sauti.ui.compose.SautiTheme
import io.sauti.ui.compose.SautiTypography
import io.sauti.ui.compose.overlay.CallEndGlyph
import io.sauti.ui.compose.overlay.PhoneGlyph
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SautiIncomingCallScreen(
    callerName: String,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
    colors: SautiColors? = null,
    typography: SautiTypography? = null,
    strings: SautiStrings = SautiStrings(),
    requiresSlide: Boolean = true,
    avatar: (@Composable () -> Unit)? = null,
    status: (@Composable () -> Unit)? = null
) {
    SautiTheme(colors = colors, typography = typography, strings = strings) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(SautiTheme.colors.surface)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.padding(top = 64.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (avatar != null) avatar() else SautiAvatar(callerName)
                Text(
                    text = callerName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = SautiTheme.colors.onSurface
                )
                if (status != null) status() else DefaultStatus(strings)
            }
            if (requiresSlide) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    SlideToAnswer(strings = strings, onAccept = onAccept)
                    SautiIconButton(
                        contentDescription = strings.decline,
                        backgroundColor = SautiTheme.colors.danger,
                        contentColor = SautiTheme.colors.onDanger,
                        onClick = onDecline,
                        size = 64.dp,
                        glyph = { c, m -> CallEndGlyph(c, m) }
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(56.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SautiIconButton(
                        contentDescription = strings.decline,
                        backgroundColor = SautiTheme.colors.danger,
                        contentColor = SautiTheme.colors.onDanger,
                        onClick = onDecline,
                        size = 72.dp,
                        glyph = { c, m -> CallEndGlyph(c, m) }
                    )
                    SautiIconButton(
                        contentDescription = strings.accept,
                        backgroundColor = SautiTheme.colors.positive,
                        contentColor = SautiTheme.colors.onPositive,
                        onClick = onAccept,
                        size = 72.dp,
                        glyph = { c, m -> PhoneGlyph(c, m) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SlideToAnswer(
    strings: SautiStrings,
    onAccept: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SautiTheme.colors
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val trackHeight = 72.dp
    val handleSize = 60.dp
    val handlePx = with(density) { handleSize.toPx() }
    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var accepted by remember { mutableStateOf(false) }

    fun travel(): Float = (trackWidthPx - handlePx).coerceAtLeast(0f)

    fun fireAccept() {
        if (accepted) return
        accepted = true
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        onAccept()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(trackHeight)
            .clip(RoundedCornerShape(trackHeight / 2))
            .background(colors.controlIdleBackground)
            .onSizeChanged { trackWidthPx = it.width.toFloat() }
            .semantics(mergeDescendants = true) {
                contentDescription = strings.slideToAnswer
                onClick(label = strings.accept) {
                    fireAccept()
                    true
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = strings.slideToAnswer,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceMuted,
            modifier = Modifier.align(Alignment.Center)
        )
        Box(
            modifier = Modifier
                .padding(6.dp)
                .size(handleSize)
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .clip(CircleShape)
                .background(colors.positive)
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        offsetX = (offsetX + delta).coerceIn(0f, travel())
                    },
                    onDragStopped = {
                        if (IncomingSlideDecision.accepts(offsetX, travel())) {
                            fireAccept()
                        } else {
                            scope.launch { animate(offsetX, 0f) { value, _ -> offsetX = value } }
                        }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            PhoneGlyph(colors.onPositive, Modifier.size(26.dp))
        }
    }
}

@Composable
private fun DefaultStatus(strings: SautiStrings) {
    Text(
        text = strings.incomingCallTitle,
        style = MaterialTheme.typography.bodyMedium,
        color = SautiTheme.colors.onSurfaceMuted,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    )
}
