package io.sauti.ui.compose

import io.sauti.android.SautiCall
import io.sauti.engine.CallPhase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

data class SautiCallSummary(
    val active: Boolean,
    val connected: Boolean,
    val peerLabel: String?,
    val durationMs: Long
) {
    companion object {
        val Inactive = SautiCallSummary(active = false, connected = false, peerLabel = null, durationMs = 0L)
    }
}

internal fun summaryFrom(uiState: SautiCallUiState): SautiCallSummary =
    SautiCallSummary(
        active = uiState.phase != CallPhase.LEFT,
        connected = uiState.phase == CallPhase.CONNECTED,
        peerLabel = uiState.others.firstOrNull()?.label,
        durationMs = uiState.durationMs
    )

@OptIn(ExperimentalCoroutinesApi::class)
internal fun callSummaryFlow(source: Flow<SautiCall?>): Flow<SautiCallSummary> =
    source
        .flatMapLatest { call ->
            if (call == null) {
                flowOf(SautiCallSummary.Inactive)
            } else {
                sautiCallUiStateFlow(call, call.selfParticipantId).map(::summaryFrom)
            }
        }
        .distinctUntilChanged()
