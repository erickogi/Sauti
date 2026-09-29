package io.sauti.ui.compose

import io.sauti.android.SautiCall
import io.sauti.android.audio.AudioDevice
import io.sauti.engine.CallEvent
import io.sauti.engine.CallPhase
import io.sauti.engine.CallState
import io.sauti.engine.ConnectionState
import io.sauti.engine.ParticipantSnapshot
import io.sauti.engine.Quality
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SautiCallSummaryTest {

    private fun controller() = object : SautiCallController {
        override fun setMuted(muted: Boolean) {}
        override fun setHold(onHold: Boolean) {}
        override fun selectDevice(device: AudioDevice) {}
        override fun leave() {}
    }

    private suspend fun uiStateOf(state: CallState, self: String?): SautiCallUiState {
        val device = MutableStateFlow(AudioDevice.EARPIECE)
        val devices = MutableStateFlow(setOf(AudioDevice.EARPIECE))
        val interrupted = MutableStateFlow(false)
        return callUiStateFlow(MutableStateFlow(state), device, devices, interrupted, self, controller()).first()
    }

    @Test
    fun connectedWithPeerIsActiveConnectedWithLabelAndDuration() = runTest {
        val peer = ParticipantSnapshot(
            participantId = "peer",
            metadata = mapOf("name" to JsonPrimitive("Ada")),
            muted = false,
            onHold = false,
            connectionState = ConnectionState.CONNECTED,
            quality = Quality.GOOD
        )
        val ui = uiStateOf(
            CallState(phase = CallPhase.CONNECTED, participants = listOf(peer), durationMs = 5_000),
            self = "me"
        )
        val summary = summaryFrom(ui)
        assertTrue(summary.active)
        assertTrue(summary.connected)
        assertEquals("Ada", summary.peerLabel)
        assertEquals(5_000, summary.durationMs)
    }

    @Test
    fun connectingIsActiveNotConnectedNoPeer() = runTest {
        val summary = summaryFrom(uiStateOf(CallState(phase = CallPhase.CONNECTING), self = "me"))
        assertTrue(summary.active)
        assertFalse(summary.connected)
        assertNull(summary.peerLabel)
    }

    @Test
    fun leftIsInactive() = runTest {
        val summary = summaryFrom(uiStateOf(CallState(phase = CallPhase.LEFT), self = "me"))
        assertFalse(summary.active)
        assertFalse(summary.connected)
    }

    @Test
    fun peerLabelNeverComesFromSelf() = runTest {
        val self = ParticipantSnapshot(
            participantId = "me",
            metadata = mapOf("name" to JsonPrimitive("Me")),
            muted = false,
            onHold = false,
            connectionState = ConnectionState.CONNECTED,
            quality = Quality.GOOD
        )
        val ui = uiStateOf(
            CallState(phase = CallPhase.CONNECTED, participants = listOf(self)),
            self = "me"
        )
        assertNull(summaryFrom(ui).peerLabel)
    }

    @Test
    fun flowEmitsInactiveWhenSourceIsNull() = runTest {
        val source = MutableStateFlow<SautiCall?>(null)
        assertEquals(SautiCallSummary.Inactive, callSummaryFlow(source).first())
    }

    @Test
    fun flowEmitsActiveSummaryForLiveCallThenInactiveOnEnd() = runTest {
        val call = FakeCall("me", MutableStateFlow(CallState(phase = CallPhase.CONNECTED, durationMs = 1_000)))
        val source = MutableStateFlow<SautiCall?>(call)

        val active = callSummaryFlow(source).first()
        assertTrue(active.active)
        assertTrue(active.connected)

        source.value = null
        assertEquals(SautiCallSummary.Inactive, callSummaryFlow(source).first())
    }

    private class FakeCall(
        override val selfParticipantId: String?,
        private val stateFlow: MutableStateFlow<CallState>
    ) : SautiCall {
        override val state: StateFlow<CallState> get() = stateFlow
        override val events: SharedFlow<CallEvent> = MutableSharedFlow()
        override val currentDevice: StateFlow<AudioDevice> = MutableStateFlow(AudioDevice.EARPIECE)
        override val availableDevices: StateFlow<Set<AudioDevice>> = MutableStateFlow(setOf(AudioDevice.EARPIECE))
        override val interrupted: StateFlow<Boolean> = MutableStateFlow(false)
        override fun setMuted(muted: Boolean) {}
        override fun setHold(onHold: Boolean) {}
        override fun selectDevice(device: AudioDevice) {}
        override fun hangUp() {}
    }
}
