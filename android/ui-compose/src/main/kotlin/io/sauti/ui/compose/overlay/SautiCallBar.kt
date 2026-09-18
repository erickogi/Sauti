package io.sauti.ui.compose.overlay

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.sauti.ui.compose.LocalSautiStrings
import io.sauti.ui.compose.SautiTheme
import io.sauti.ui.compose.formatDuration

@Composable
fun SautiCallBar(
    peerName: String,
    durationMs: Long,
    onReturn: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SautiTheme.colors
    val strings = LocalSautiStrings.current
    val pulse = rememberInfiniteTransition(label = "callBarPulse")
    val dotAlpha by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "callBarDot"
    )
    val subtitle = listOf(peerName, formatDuration(durationMs))
        .filter { it.isNotBlank() }
        .joinToString(" · ")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(6.dp)
            .background(colors.callBar)
            .clickable(onClick = onReturn)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .semantics { contentDescription = strings.callBarReturn },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .alpha(dotAlpha)
                .clip(CircleShape)
                .background(colors.onCallBar)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = strings.callBarReturn,
                color = colors.onCallBar,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            if (subtitle.isNotBlank()) {
                Text(text = subtitle, color = colors.onCallBar, fontSize = 12.sp)
            }
        }
        Text(text = "›", color = colors.onCallBar, fontSize = 20.sp)
    }
}
