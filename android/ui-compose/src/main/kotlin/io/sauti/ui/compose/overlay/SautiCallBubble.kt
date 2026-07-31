package io.sauti.ui.compose.overlay

import android.content.Context
import io.sauti.android.incoming.DefaultForegroundProbe
import io.sauti.android.incoming.ForegroundProbe
import io.sauti.android.overlay.BubbleReducer
import io.sauti.android.overlay.BubbleVisibility
import io.sauti.android.overlay.DefaultOverlayPermission
import io.sauti.android.overlay.OverlayPermission
import io.sauti.android.overlay.bubbleCallActive
import io.sauti.ui.compose.SautiCallUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class SautiCallBubble internal constructor(
    private val uiStateFlow: Flow<SautiCallUiState>,
    private val optedIn: Boolean,
    private val foreground: ForegroundProbe,
    private val permission: OverlayPermission,
    private val host: BubbleOverlayHost,
    private val scope: CoroutineScope
) {
    constructor(
        context: Context,
        uiStateFlow: Flow<SautiCallUiState>,
        optedIn: Boolean,
        onReturn: () -> Unit,
        foreground: ForegroundProbe = DefaultForegroundProbe(context),
        permission: OverlayPermission = DefaultOverlayPermission(context),
        host: BubbleOverlayHost = WindowManagerOverlayHost(context, onReturn)
    ) : this(
        uiStateFlow = uiStateFlow,
        optedIn = optedIn,
        foreground = foreground,
        permission = permission,
        host = host,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    )

    private var job: Job? = null
    private var shown = false

    fun start() {
        if (job != null) return
        job = scope.launch {
            uiStateFlow.collect { evaluate(it) }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        host.hide()
        shown = false
    }

    private fun evaluate(uiState: SautiCallUiState) {
        val visibility = BubbleReducer.reduce(
            bubbleCallActive(uiState.phase),
            foreground.isForeground(),
            permission.granted(),
            optedIn
        )
        if (visibility == BubbleVisibility.Shown) {
            host.show(uiState)
            shown = true
        } else if (shown) {
            host.hide()
            shown = false
        }
    }
}
