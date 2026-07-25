package io.sauti.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun ParticipantList(
    uiState: SautiCallUiState,
    modifier: Modifier = Modifier,
    row: @Composable (SautiParticipantRow) -> Unit = { ParticipantItem(it) }
) {
    val strings = LocalSautiStrings.current
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (uiState.roster.isEmpty()) {
            Text(
                text = strings.waitingForPeer,
                style = SautiTheme.typography.label,
                color = SautiTheme.colors.onSurfaceMuted
            )
            return@Column
        }
        uiState.roster.forEach { entry -> row(entry) }
    }
}

@Composable
fun ParticipantItem(row: SautiParticipantRow, modifier: Modifier = Modifier) {
    val strings = LocalSautiStrings.current
    val name = if (row.isSelf) strings.you else row.label
    val statusText = participantStatusText(row, strings)
    val description = if (statusText.isEmpty()) name else name + ", " + statusText
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .semantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = name,
            style = SautiTheme.typography.label,
            color = SautiTheme.colors.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (row.muted) {
            Text(
                text = strings.muted,
                style = SautiTheme.typography.caption,
                color = SautiTheme.colors.onSurfaceMuted
            )
        }
        QualityIndicator(quality = row.quality)
    }
}

internal fun participantStatusText(row: SautiParticipantRow, strings: SautiStrings): String {
    val parts = mutableListOf<String>()
    if (row.muted) parts.add(strings.muted)
    if (row.onHold) parts.add(strings.onHold)
    return parts.joinToString(separator = ", ")
}
