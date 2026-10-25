package io.sauti.ui.compose

import android.provider.Settings
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
internal fun sautiPulseAnimated(): Boolean {
    val context = LocalContext.current
    val scale = remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
        }.getOrDefault(1f)
    }
    return pulseAnimated(scale)
}

@Composable
fun SautiCallingPulse(
    modifier: Modifier = Modifier,
    animated: Boolean = sautiPulseAnimated(),
    content: @Composable () -> Unit
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (animated) {
            val transition = rememberInfiniteTransition(label = "sautiCallingPulse")
            SautiPulseRing(transition = transition, startOffsetMillis = 0)
            SautiPulseRing(transition = transition, startOffsetMillis = PULSE_PERIOD_MS / 2)
        }
        content()
    }
}

@Composable
private fun SautiPulseRing(transition: InfiniteTransition, startOffsetMillis: Int) {
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = PULSE_PERIOD_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
            initialStartOffset = StartOffset(startOffsetMillis)
        ),
        label = "sautiCallingPulseRing"
    )
    Box(
        modifier = Modifier
            .size(PULSE_BASE_SIZE.dp)
            .graphicsLayer {
                val ringScale = PULSE_MIN_SCALE + (PULSE_MAX_SCALE - PULSE_MIN_SCALE) * progress
                scaleX = ringScale
                scaleY = ringScale
                alpha = (1f - progress) * PULSE_MAX_ALPHA
            }
            .clip(CircleShape)
            .border(PULSE_RING_WIDTH.dp, SautiTheme.colors.accent, CircleShape)
    )
}

private const val PULSE_PERIOD_MS = 1600
private const val PULSE_BASE_SIZE = 96
private const val PULSE_RING_WIDTH = 2
private const val PULSE_MIN_SCALE = 1f
private const val PULSE_MAX_SCALE = 1.8f
private const val PULSE_MAX_ALPHA = 0.5f
