package io.sauti.ui.compose.incoming

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.sauti.ui.compose.SautiAvatar
import io.sauti.ui.compose.SautiColors
import io.sauti.ui.compose.SautiIconButton
import io.sauti.ui.compose.SautiStrings
import io.sauti.ui.compose.SautiTheme
import io.sauti.ui.compose.SautiTypography
import io.sauti.ui.compose.overlay.CallEndGlyph
import io.sauti.ui.compose.overlay.PhoneGlyph

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

@Composable
private fun DefaultStatus(strings: SautiStrings) {
    Text(
        text = strings.incomingCallTitle,
        style = MaterialTheme.typography.bodyMedium,
        color = SautiTheme.colors.onSurfaceMuted,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    )
}
