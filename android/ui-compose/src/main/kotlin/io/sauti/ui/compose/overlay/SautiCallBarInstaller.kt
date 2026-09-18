package io.sauti.ui.compose.overlay

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import io.sauti.android.service.CallForegroundService
import io.sauti.ui.compose.SautiCallActivity
import io.sauti.ui.compose.rememberSautiCallUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

typealias SautiCallBarContent = @Composable (peerName: String, durationMs: Long, onReturn: () -> Unit) -> Unit

fun installSautiCallBar(
    application: Application,
    onReturn: () -> Unit,
    optedIn: Boolean = true,
    theme: SautiBubbleTheme,
    content: SautiCallBarContent? = null
) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    var current: Activity? = null
    var attachedTo: Activity? = null
    var barView: View? = null
    var callActive = false

    fun detach() {
        val view = barView ?: return
        (view.parent as? ViewGroup)?.removeView(view)
        barView = null
        attachedTo = null
    }

    fun attach(activity: Activity) {
        val root = activity.findViewById<FrameLayout>(android.R.id.content) ?: return
        val view = ComposeView(activity).apply {
            setContent {
                val call by CallForegroundService.call.collectAsState()
                val active = call
                if (active != null) {
                    val uiState = rememberSautiCallUiState(active, active.selfParticipantId)
                    val peer = uiState.others.firstOrNull()?.label.orEmpty()
                    theme {
                        val slot = content
                        if (slot != null) {
                            slot(peer, uiState.durationMs, onReturn)
                        } else {
                            SautiCallBar(peerName = peer, durationMs = uiState.durationMs, onReturn = onReturn)
                        }
                    }
                }
            }
        }
        root.addView(
            view,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            )
        )
        barView = view
        attachedTo = activity
    }

    fun sync() {
        val onCallScreen = current is SautiCallActivity
        val show = current != null && CallBarReducer.visible(optedIn, callActive, onCallScreen)
        if (show) {
            if (attachedTo !== current) {
                detach()
                current?.let { attach(it) }
            }
        } else {
            detach()
        }
    }

    application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity) {
            current = activity
            sync()
        }

        override fun onActivityPaused(activity: Activity) {
            if (current === activity) current = null
            if (attachedTo === activity) detach()
        }

        override fun onActivityDestroyed(activity: Activity) {
            if (attachedTo === activity) detach()
            if (current === activity) current = null
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    })

    scope.launch {
        CallForegroundService.call.collect {
            callActive = it != null
            sync()
        }
    }
}
