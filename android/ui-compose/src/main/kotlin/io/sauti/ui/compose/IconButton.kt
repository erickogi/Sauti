package io.sauti.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun SautiIconButton(
    contentDescription: String,
    backgroundColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    glyph: @Composable (Color, Modifier) -> Unit
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        glyph(contentColor, Modifier.size(size * 0.44f))
    }
}
