package io.sauti.ui.compose.overlay

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import io.sauti.android.overlay.BubbleConfig
import io.sauti.android.service.CallForegroundService
import io.sauti.ui.compose.sautiCallUiStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

typealias SautiBubbleTheme = @Composable (@Composable () -> Unit) -> Unit

fun installSautiCallBubble(
    application: Application,
    onReturn: () -> Unit,
    optedIn: Boolean,
    theme: SautiBubbleTheme,
    config: BubbleConfig = BubbleConfig()
) = installSautiCallBubble(
    application = application,
    onReturn = onReturn,
    host = WindowManagerOverlayHost(application, onReturn, theme, config),
    optedIn = optedIn
)

fun installSautiCallBubble(
    application: Application,
    onReturn: () -> Unit,
    host: BubbleOverlayHost,
    optedIn: Boolean
) {
    val foreground = ProcessForegroundFlow()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    var bubble: SautiCallBubble? = null
    scope.launch {
        CallForegroundService.call.collect { call ->
            bubble?.stop()
            bubble = null
            if (call != null) {
                bubble = SautiCallBubble(
                    context = application,
                    uiStateFlow = sautiCallUiStateFlow(call, call.selfParticipantId),
                    optedIn = optedIn,
                    onReturn = onReturn,
                    host = host,
                    foregroundFlow = foreground.flow
                ).also { it.start() }
            }
        }
    }
}

class ProcessForegroundFlow {
    private val state = MutableStateFlow(true)
    val flow: StateFlow<Boolean> get() = state.asStateFlow()

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                state.value = true
            }

            override fun onStop(owner: LifecycleOwner) {
                state.value = false
            }
        })
    }
}
