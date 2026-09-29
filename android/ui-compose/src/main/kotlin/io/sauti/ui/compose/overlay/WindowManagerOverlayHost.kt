package io.sauti.ui.compose.overlay

import android.annotation.SuppressLint
import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
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
import io.sauti.android.overlay.BubbleBounds
import io.sauti.android.overlay.BubbleConfig
import io.sauti.android.overlay.BubbleExpansion
import io.sauti.android.overlay.BubbleExpansionReducer
import io.sauti.android.overlay.BubblePosition
import io.sauti.android.overlay.clampToBounds
import io.sauti.android.overlay.exceedsSlop
import io.sauti.android.overlay.initialPosition
import io.sauti.android.overlay.isTap
import io.sauti.android.overlay.reclampToBounds
import io.sauti.android.overlay.snapToNearestEdge
import io.sauti.ui.compose.SautiCallUiState
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
    private val onReturn: () -> Unit,
    private val theme: SautiBubbleTheme = { content -> SautiTheme { content() } },
    private val config: BubbleConfig = BubbleConfig()
) : BubbleOverlayHost {

    private val windowManager: WindowManager
        get() = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var view: ComposeView? = null
    private var owner: OverlayViewTreeOwner? = null
    private var params: WindowManager.LayoutParams? = null
    private var current by mutableStateOf<SautiCallUiState?>(null)
    private var expansion by mutableStateOf(BubbleExpansion.Collapsed)
    private var position = BubblePosition(0, 0)
    private var positioned = false
    private var downRawX = 0f
    private var downRawY = 0f
    private var downX = 0
    private var downY = 0
    private var dragCollapsed = false
    private var configCallbacks: ComponentCallbacks? = null

    @RequiresApi(Build.VERSION_CODES.O)
    override fun show(uiState: SautiCallUiState) {
        current = uiState
        if (view != null) return
        if (!positioned) {
            position = initialPosition(collapsedBounds(), config.initialEdge)
        }
        val viewTreeOwner = OverlayViewTreeOwner().apply { attach() }
        val lp = layoutParams()
        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(viewTreeOwner)
            setViewTreeViewModelStoreOwner(viewTreeOwner)
            setViewTreeSavedStateRegistryOwner(viewTreeOwner)
            setContent {
                theme {
                    current?.let { state ->
                        SautiCallBubbleContent(
                            uiState = state,
                            expansion = expansion,
                            onReturn = onReturn,
                            onToggleMute = { state.toggleMute() },
                            onEnd = { state.leave() },
                            onCollapse = { expansion = BubbleExpansionReducer.onReturn() }
                        )
                    }
                }
            }
        }
        attachTouchListener(composeView, lp)
        windowManager.addView(composeView, lp)
        view = composeView
        owner = viewTreeOwner
        params = lp
        registerConfigCallbacks()
        seedPosition(composeView, lp)
    }

    override fun hide() {
        val attached = view ?: return
        configCallbacks?.let { context.unregisterComponentCallbacks(it) }
        configCallbacks = null
        windowManager.removeView(attached)
        owner?.detach()
        view = null
        owner = null
        params = null
        current = null
        expansion = BubbleExpansionReducer.onHidden()
        positioned = false
        position = BubblePosition(0, 0)
        dragCollapsed = false
    }

    private fun registerConfigCallbacks() {
        val callbacks = object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) = onConfigurationChanged()
            @Suppress("OVERRIDE_DEPRECATION")
            override fun onLowMemory() = Unit
        }
        context.registerComponentCallbacks(callbacks)
        configCallbacks = callbacks
    }

    private fun onConfigurationChanged() {
        val attached = view ?: return
        val lp = params ?: return
        if (!positioned) return
        applyPosition(reclampToBounds(BubblePosition(lp.x, lp.y), bounds(attached)), lp)
    }

    private fun seedPosition(composeView: ComposeView, lp: WindowManager.LayoutParams) {
        composeView.post {
            if (positioned || view !== composeView) return@post
            val seeded = initialPosition(bounds(composeView), config.initialEdge)
            positioned = true
            applyPosition(seeded, lp)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachTouchListener(composeView: ComposeView, lp: WindowManager.LayoutParams) {
        composeView.setOnTouchListener { _, event -> handleTouch(composeView, event, lp) }
    }

    private fun handleTouch(
        composeView: ComposeView,
        event: MotionEvent,
        lp: WindowManager.LayoutParams
    ): Boolean = when (event.action) {
        MotionEvent.ACTION_DOWN -> {
            downRawX = event.rawX
            downRawY = event.rawY
            downX = lp.x
            downY = lp.y
            dragCollapsed = false
            true
        }
        MotionEvent.ACTION_MOVE -> {
            val dx = (event.rawX - downRawX).toInt()
            val dy = (event.rawY - downRawY).toInt()
            if (!dragCollapsed && expansion == BubbleExpansion.Expanded &&
                exceedsSlop(event.rawX - downRawX, event.rawY - downRawY, touchSlop())
            ) {
                expansion = BubbleExpansionReducer.onDragStart(expansion)
                dragCollapsed = true
            }
            val moveBounds = if (dragCollapsed) collapsedBounds() else bounds(composeView)
            applyPosition(clampToBounds(BubblePosition(downX + dx, downY + dy), moveBounds), lp)
            true
        }
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
            val snapBounds = if (dragCollapsed) collapsedBounds() else bounds(composeView)
            if (isTap(event.rawX - downRawX, event.rawY - downRawY, touchSlop())) {
                expansion = BubbleExpansionReducer.onTap(expansion)
            } else {
                applyPosition(snapToNearestEdge(BubblePosition(lp.x, lp.y), snapBounds), lp)
            }
            dragCollapsed = false
            true
        }
        else -> false
    }

    private fun touchSlop(): Float = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

    private fun applyPosition(pos: BubblePosition, lp: WindowManager.LayoutParams) {
        position = pos
        lp.x = pos.x
        lp.y = pos.y
        view?.let { windowManager.updateViewLayout(it, lp) }
    }

    private fun bounds(composeView: View): BubbleBounds {
        val metrics = context.resources.displayMetrics
        val margin = (config.edgeMarginDp * metrics.density).toInt()
        return BubbleBounds(
            screenWidth = metrics.widthPixels,
            screenHeight = metrics.heightPixels,
            bubbleWidth = composeView.width,
            bubbleHeight = composeView.height,
            margin = margin
        )
    }

    private fun collapsedBounds(): BubbleBounds {
        val metrics = context.resources.displayMetrics
        val size = (config.collapsedSizeDp * metrics.density).toInt()
        val margin = (config.edgeMarginDp * metrics.density).toInt()
        return BubbleBounds(
            screenWidth = metrics.widthPixels,
            screenHeight = metrics.heightPixels,
            bubbleWidth = size,
            bubbleHeight = size,
            margin = margin
        )
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
            gravity = Gravity.TOP or Gravity.START
            x = position.x
            y = position.y
        }
}
