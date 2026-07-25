package io.sauti.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.sauti.android.SautiClient
import io.sauti.engine.ParticipantSnapshot

@Composable
fun SautiCallScreen(
    client: SautiClient,
    selfParticipantId: String? = null,
    modifier: Modifier = Modifier,
    strings: SautiStrings = SautiStrings(),
    controlBar: (@Composable (SautiCallUiState) -> Unit)? = null,
    participantItem: (@Composable (ParticipantSnapshot) -> Unit)? = null
) {
    SautiTheme(strings = strings) {
        val uiState = rememberSautiCallUiState(client, selfParticipantId)
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(SautiTheme.colors.surface)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CallStatus(uiState = uiState)
                QualityIndicator(quality = uiState.quality)
            }
            DurationTimer(durationMs = uiState.durationMs)
            InterruptionBanner(uiState = uiState)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (participantItem != null) {
                    uiState.orderedParticipants.forEach { snapshot -> participantItem(snapshot) }
                } else {
                    ParticipantList(uiState = uiState)
                }
            }
            Spacer(modifier = Modifier.padding(4.dp))
            if (controlBar != null) {
                controlBar(uiState)
            } else {
                ControlBar(uiState = uiState, onEnd = { uiState.leave() })
            }
        }
    }
}
