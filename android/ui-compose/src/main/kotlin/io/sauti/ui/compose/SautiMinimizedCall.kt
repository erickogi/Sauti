package io.sauti.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.sauti.engine.CallPhase

fun showsDuration(uiState: SautiCallUiState): Boolean = uiState.phase == CallPhase.CONNECTED

fun minimizedContentDescription(uiState: SautiCallUiState, strings: SautiStrings): String {
    val status = callStatusText(uiState, strings)
    return if (showsDuration(uiState)) status + " " + formatDuration(uiState.durationMs) else status
}

@Composable
fun SautiMinimizedCall(
    uiState: SautiCallUiState,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    onEnd: () -> Unit = { uiState.leave() }
) {
    val strings = LocalSautiStrings.current
    Row(
        modifier = modifier
            .clip(SautiTheme.shapes.surface)
            .background(SautiTheme.colors.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .defaultMinSize(minWidth = MIN_TOUCH_TARGET, minHeight = MIN_TOUCH_TARGET)
                .clip(SautiTheme.shapes.surface)
                .clickable(onClick = onExpand)
                .padding(horizontal = 8.dp)
                .semantics {
                    role = Role.Button
                    contentDescription = minimizedContentDescription(uiState, strings)
                },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CallStatus(uiState = uiState)
            if (showsDuration(uiState)) {
                DurationTimer(durationMs = uiState.durationMs)
            }
        }
        MuteButton(
            uiState = uiState,
            modifier = Modifier.defaultMinSize(minWidth = MIN_TOUCH_TARGET, minHeight = MIN_TOUCH_TARGET)
        )
        EndCallButton(
            onEnd = onEnd,
            modifier = Modifier.defaultMinSize(minWidth = MIN_TOUCH_TARGET, minHeight = MIN_TOUCH_TARGET)
        )
    }
}

private val MIN_TOUCH_TARGET = 48.dp
