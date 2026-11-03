package io.sauti.ui.compose.overlay

import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import io.sauti.android.audio.AudioDevice
import io.sauti.engine.CallPhase
import io.sauti.engine.CallState
import io.sauti.ui.compose.SautiCallController
import io.sauti.ui.compose.SautiCallUiState
import io.sauti.ui.compose.buildSautiCallUiState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

private class LeakNoopController : SautiCallController {
    override fun setMuted(muted: Boolean) {}
    override fun setHold(onHold: Boolean) {}
    override fun selectDevice(device: AudioDevice) {}
    override fun leave() {}
}

private class ThrowingWindowManager(real: WindowManager) : WindowManager by real {
    override fun addView(view: View, params: ViewGroup.LayoutParams) {
        throw RuntimeException("overlay add failed")
    }
}

private class ThrowingWmContext(base: Context, private val wm: WindowManager) : ContextWrapper(base) {
    override fun getSystemService(name: String): Any? =
        if (name == Context.WINDOW_SERVICE) wm else super.getSystemService(name)
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WindowManagerOverlayHostLeakTest {

    private fun uiState(): SautiCallUiState = buildSautiCallUiState(
        state = CallState(phase = CallPhase.CONNECTED),
        currentDevice = AudioDevice.EARPIECE,
        availableDevices = setOf(AudioDevice.EARPIECE),
        interrupted = false,
        selfParticipantId = null,
        controller = LeakNoopController()
    )

    @Test
    fun addViewFailureDetachesTheOwner() {
        val base: Context = ApplicationProvider.getApplicationContext()
        val realWm = base.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val context = ThrowingWmContext(base, ThrowingWindowManager(realWm))

        var captured: OverlayViewTreeOwner? = null
        val host = WindowManagerOverlayHost(context = context, onReturn = {})
        host.ownerFactory = { OverlayViewTreeOwner().also { captured = it } }

        host.show(uiState())

        assertEquals(Lifecycle.State.DESTROYED, captured!!.lifecycle.currentState)
    }
}
