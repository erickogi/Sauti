package io.sauti.ui.compose.incoming

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.sauti.ui.compose.SautiColors
import io.sauti.ui.compose.SautiStrings
import io.sauti.ui.compose.SautiTheme
import io.sauti.ui.compose.SautiTypography

@Composable
fun SautiIncomingCallScreen(
    callerName: String,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
    colors: SautiColors? = null,
    typography: SautiTypography? = null,
    strings: SautiStrings = SautiStrings(),
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
                modifier = Modifier.padding(top = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (avatar != null) avatar() else DefaultAvatar(callerName)
                Text(
                    text = callerName,
                    style = SautiTheme.typography.status,
                    color = SautiTheme.colors.onSurface
                )
                if (status != null) status() else DefaultStatus(strings)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CallActionButton(
                    label = strings.decline,
                    containerColor = SautiTheme.colors.danger,
                    contentColor = SautiTheme.colors.onDanger,
                    onClick = onDecline
                )
                CallActionButton(
                    label = strings.accept,
                    containerColor = SautiTheme.colors.accent,
                    contentColor = SautiTheme.colors.onDanger,
                    onClick = onAccept
                )
            }
        }
    }
}

@Composable
private fun DefaultStatus(strings: SautiStrings) {
    Text(
        text = strings.incomingCallTitle,
        style = SautiTheme.typography.caption,
        color = SautiTheme.colors.onSurfaceMuted,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    )
}

@Composable
private fun DefaultAvatar(callerName: String) {
    val initial = callerName.trim().firstOrNull()?.uppercaseChar()?.toString().orEmpty()
    Box(
        modifier = Modifier
            .size(96.dp)
            .clip(CircleShape)
            .background(SautiTheme.colors.accent),
        contentAlignment = Alignment.Center
    ) {
        if (initial.isNotEmpty()) {
            Text(
                text = initial,
                style = SautiTheme.typography.status,
                color = SautiTheme.colors.onDanger
            )
        }
    }
}

@Composable
private fun CallActionButton(
    label: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        shape = SautiTheme.shapes.control,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        modifier = Modifier
            .heightIn(min = 72.dp)
            .widthIn(min = 72.dp)
            .semantics { contentDescription = label }
    ) {
        Text(text = label)
    }
}
