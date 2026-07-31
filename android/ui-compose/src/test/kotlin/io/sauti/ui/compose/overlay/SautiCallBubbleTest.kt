package io.sauti.ui.compose.overlay

import io.sauti.android.audio.AudioDevice
import io.sauti.android.incoming.ForegroundProbe
import io.sauti.android.overlay.OverlayPermission
import io.sauti.engine.CallPhase
import io.sauti.engine.CallState
import io.sauti.ui.compose.SautiCallController
import io.sauti.ui.compose.SautiCallUiState
import io.sauti.ui.compose.buildSautiCallUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private class NoopController : SautiCallController {
    override fun setMuted(muted: Boolean) {}
    override fun setHold(onHold: Boolean) {}
    override fun selectDevice(device: AudioDevice) {}
    override fun leave() {}
}

private class RecordingHost : BubbleOverlayHost {
    val shows = mutableListOf<CallPhase>()
    var hides = 0

    override fun show(uiState: SautiCallUiState) {
        shows.add(uiState.phase)
    }

    override fun hide() {
        hides += 1
    }
}

private class FakeForeground(var value: Boolean) : ForegroundProbe {
    override fun isForeground(): Boolean = value
}

private class FakePermission(var value: Boolean) : OverlayPermission {
    override fun granted(): Boolean = value
}

private fun uiState(phase: CallPhase): SautiCallUiState = buildSautiCallUiState(
    state = CallState(phase = phase),
    currentDevice = AudioDevice.EARPIECE,
    availableDevices = setOf(AudioDevice.EARPIECE),
    interrupted = false,
    selfParticipantId = null,
    controller = NoopController()
)

@OptIn(ExperimentalCoroutinesApi::class)
class SautiCallBubbleTest {

    @Test
    fun showsWhenOptedInPermittedActiveAndBackgrounded() = runTest {
        val host = RecordingHost()
        val flow = MutableStateFlow(uiState(CallPhase.IDLE))
        val bubble = SautiCallBubble(
            uiStateFlow = flow,
            optedIn = true,
            foreground = FakeForeground(false),
            permission = FakePermission(true),
            host = host,
            scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        )

        bubble.start()
        flow.value = uiState(CallPhase.CONNECTED)
        advanceUntilIdle()

        assertEquals(listOf(CallPhase.CONNECTED), host.shows)
        assertEquals(0, host.hides)
        bubble.stop()
    }

    @Test
    fun connectingAlsoShows() = runTest {
        val host = RecordingHost()
        val flow = MutableStateFlow(uiState(CallPhase.IDLE))
        val bubble = SautiCallBubble(
            uiStateFlow = flow,
            optedIn = true,
            foreground = FakeForeground(false),
            permission = FakePermission(true),
            host = host,
            scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        )

        bubble.start()
        flow.value = uiState(CallPhase.CONNECTING)
        advanceUntilIdle()

        assertEquals(listOf(CallPhase.CONNECTING), host.shows)
        bubble.stop()
    }

    @Test
    fun foregroundedDoesNotShow() = runTest {
        val host = RecordingHost()
        val flow = MutableStateFlow(uiState(CallPhase.IDLE))
        val bubble = SautiCallBubble(
            uiStateFlow = flow,
            optedIn = true,
            foreground = FakeForeground(true),
            permission = FakePermission(true),
            host = host,
            scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        )

        bubble.start()
        flow.value = uiState(CallPhase.CONNECTED)
        advanceUntilIdle()

        assertEquals(emptyList(), host.shows)
        assertEquals(0, host.hides)
        bubble.stop()
    }

    @Test
    fun withoutPermissionDoesNotShow() = runTest {
        val host = RecordingHost()
        val flow = MutableStateFlow(uiState(CallPhase.IDLE))
        val bubble = SautiCallBubble(
            uiStateFlow = flow,
            optedIn = true,
            foreground = FakeForeground(false),
            permission = FakePermission(false),
            host = host,
            scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        )

        bubble.start()
        flow.value = uiState(CallPhase.CONNECTED)
        advanceUntilIdle()

        assertEquals(emptyList(), host.shows)
        bubble.stop()
    }

    @Test
    fun notOptedInNeverShowsAndNoOpsHost() = runTest {
        val host = RecordingHost()
        val flow = MutableStateFlow(uiState(CallPhase.IDLE))
        val bubble = SautiCallBubble(
            uiStateFlow = flow,
            optedIn = false,
            foreground = FakeForeground(false),
            permission = FakePermission(true),
            host = host,
            scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        )

        bubble.start()
        flow.value = uiState(CallPhase.CONNECTED)
        flow.value = uiState(CallPhase.CONNECTING)
        advanceUntilIdle()

        assertEquals(emptyList(), host.shows)
        assertEquals(0, host.hides)
        bubble.stop()
    }

    @Test
    fun permissionRevokedHides() = runTest {
        val host = RecordingHost()
        val permission = FakePermission(true)
        val flow = MutableStateFlow(uiState(CallPhase.IDLE))
        val bubble = SautiCallBubble(
            uiStateFlow = flow,
            optedIn = true,
            foreground = FakeForeground(false),
            permission = permission,
            host = host,
            scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        )

        bubble.start()
        flow.value = uiState(CallPhase.CONNECTED)
        advanceUntilIdle()
        permission.value = false
        flow.value = uiState(CallPhase.CONNECTED)
        advanceUntilIdle()

        assertEquals(1, host.shows.size)
        assertEquals(1, host.hides)
        bubble.stop()
    }

    @Test
    fun foregroundEmissionHides() = runTest {
        val host = RecordingHost()
        val foreground = FakeForeground(false)
        val flow = MutableStateFlow(uiState(CallPhase.IDLE))
        val bubble = SautiCallBubble(
            uiStateFlow = flow,
            optedIn = true,
            foreground = foreground,
            permission = FakePermission(true),
            host = host,
            scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        )

        bubble.start()
        flow.value = uiState(CallPhase.CONNECTED)
        advanceUntilIdle()
        foreground.value = true
        flow.value = uiState(CallPhase.CONNECTED)
        advanceUntilIdle()

        assertEquals(1, host.shows.size)
        assertEquals(1, host.hides)
        bubble.stop()
    }

    @Test
    fun callEndedHides() = runTest {
        val host = RecordingHost()
        val flow = MutableStateFlow(uiState(CallPhase.IDLE))
        val bubble = SautiCallBubble(
            uiStateFlow = flow,
            optedIn = true,
            foreground = FakeForeground(false),
            permission = FakePermission(true),
            host = host,
            scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        )

        bubble.start()
        flow.value = uiState(CallPhase.CONNECTED)
        advanceUntilIdle()
        flow.value = uiState(CallPhase.LEFT)
        advanceUntilIdle()

        assertEquals(1, host.shows.size)
        assertEquals(1, host.hides)
        bubble.stop()
    }

    @Test
    fun stopHidesAndStopsCollecting() = runTest {
        val host = RecordingHost()
        val flow = MutableStateFlow(uiState(CallPhase.CONNECTED))
        val bubble = SautiCallBubble(
            uiStateFlow = flow,
            optedIn = true,
            foreground = FakeForeground(false),
            permission = FakePermission(true),
            host = host,
            scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        )

        bubble.start()
        advanceUntilIdle()
        bubble.stop()
        flow.value = uiState(CallPhase.CONNECTING)
        advanceUntilIdle()

        assertEquals(1, host.shows.size)
        assertEquals(1, host.hides)
    }
}
