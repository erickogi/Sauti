package io.sauti.ui.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal fun avatarInitial(name: String): String =
    name.trim().firstOrNull()?.uppercaseChar()?.toString().orEmpty()

@Composable
fun SautiAvatar(name: String, modifier: Modifier = Modifier, fallbackGlyph: Boolean = false) {
    val initial = avatarInitial(name)
    Box(
        modifier = modifier
            .size(96.dp)
            .clip(CircleShape)
            .background(SautiTheme.colors.accent),
        contentAlignment = Alignment.Center
    ) {
        if (initial.isNotEmpty()) {
            Text(
                text = initial,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = SautiTheme.colors.onAccent
            )
        } else if (fallbackGlyph) {
            PersonGlyph(
                color = SautiTheme.colors.onAccent,
                modifier = Modifier.size(44.dp)
            )
        }
    }
}

@Composable
private fun PersonGlyph(color: Color, modifier: Modifier = Modifier) {
    val path = remember { PathParser().parsePathString(PERSON_PATH).toPath() }
    Canvas(modifier = modifier) {
        val factor = size.minDimension / PERSON_VIEWPORT
        scale(factor, factor, pivot = Offset.Zero) {
            drawPath(path, color)
        }
    }
}

private const val PERSON_VIEWPORT = 24f
private const val PERSON_PATH =
    "M12,12c2.21,0 4,-1.79 4,-4s-1.79,-4 -4,-4 -4,1.79 -4,4 1.79,4 4,4zm0,2c-2.67,0 " +
        "-8,1.34 -8,4v2h16v-2c0,-2.66 -5.33,-4 -8,-4z"
