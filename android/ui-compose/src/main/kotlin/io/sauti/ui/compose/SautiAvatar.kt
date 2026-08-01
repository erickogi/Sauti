package io.sauti.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal fun avatarInitial(name: String): String =
    name.trim().firstOrNull()?.uppercaseChar()?.toString().orEmpty()

@Composable
fun SautiAvatar(name: String, modifier: Modifier = Modifier) {
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
        }
    }
}
