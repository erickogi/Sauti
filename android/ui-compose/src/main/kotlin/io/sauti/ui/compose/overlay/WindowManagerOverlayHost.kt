package io.sauti.ui.compose.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import io.sauti.ui.compose.SautiCallUiState
import io.sauti.ui.compose.SautiMinimizedCall
import io.sauti.ui.compose.SautiTheme

private class OverlayViewTreeOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    fun attach() {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    fun detach() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
    }
}

class WindowManagerOverlayHost(
    private val context: Context,
    private val onReturn: () -> Unit
) : BubbleOverlayHost {

    private val windowManager: WindowManager
        get() = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var view: ComposeView? = null
    private var owner: OverlayViewTreeOwner? = null
    private var current by mutableStateOf<SautiCallUiState?>(null)

    @RequiresApi(Build.VERSION_CODES.O)
    override fun show(uiState: SautiCallUiState) {
        current = uiState
        if (view != null) return
        val viewTreeOwner = OverlayViewTreeOwner().apply { attach() }
        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(viewTreeOwner)
            setViewTreeViewModelStoreOwner(viewTreeOwner)
            setViewTreeSavedStateRegistryOwner(viewTreeOwner)
            setContent {
                SautiTheme {
                    current?.let { SautiMinimizedCall(uiState = it, onExpand = onReturn) }
                }
            }
        }
        windowManager.addView(composeView, layoutParams())
        view = composeView
        owner = viewTreeOwner
    }

    override fun hide() {
        val attached = view ?: return
        windowManager.removeView(attached)
        owner?.detach()
        view = null
        owner = null
        current = null
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun layoutParams(): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
        }
}
