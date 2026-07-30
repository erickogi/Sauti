package io.sauti.ui.compose

import io.sauti.android.audio.AudioDevice
import io.sauti.engine.CallPhase
import io.sauti.engine.CallState
import io.sauti.engine.ConnectionState
import io.sauti.engine.ParticipantSnapshot
import io.sauti.engine.Quality
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class MinRecordingController : SautiCallController {
    val muted = mutableListOf<Boolean>()
    var left = false

    override fun setMuted(muted: Boolean) {
        this.muted.add(muted)
    }

    override fun setHold(onHold: Boolean) {}

    override fun selectDevice(device: AudioDevice) {}

    override fun leave() {
        left = true
    }
}

private fun minParticipant(
    id: String,
    connectionState: ConnectionState = ConnectionState.CONNECTED
): ParticipantSnapshot = ParticipantSnapshot(
    participantId = id,
    metadata = emptyMap(),
    muted = false,
    onHold = false,
    connectionState = connectionState,
    quality = Quality.GOOD
)

private fun minUiStateOf(
    state: CallState,
    selfParticipantId: String? = null,
    controller: SautiCallController = MinRecordingController()
): SautiCallUiState = buildSautiCallUiState(
    state = state,
    currentDevice = AudioDevice.EARPIECE,
    availableDevices = setOf(AudioDevice.EARPIECE),
    interrupted = false,
    selfParticipantId = selfParticipantId,
    controller = controller
)

class SautiMinimizedCallTest {

    @Test
    fun showsDurationOnlyWhenConnected() {
        val connected = minUiStateOf(
            CallState(
                phase = CallPhase.CONNECTED,
                participants = listOf(minParticipant("me"), minParticipant("peer")),
                durationMs = 65_000
            ),
            selfParticipantId = "me"
        )
        val connecting = minUiStateOf(CallState(phase = CallPhase.CONNECTING))

        assertTrue(showsDuration(connected))
        assertFalse(showsDuration(connecting))
    }

    @Test
    fun contentDescriptionConnectedCombinesStatusAndDuration() {
        val strings = SautiStrings()
        val connected = minUiStateOf(
            CallState(
                phase = CallPhase.CONNECTED,
                participants = listOf(minParticipant("me"), minParticipant("peer")),
                durationMs = 65_000
            ),
            selfParticipantId = "me"
        )

        assertEquals(
            strings.inCall + " " + formatDuration(65_000),
            minimizedContentDescription(connected, strings)
        )
    }

    @Test
    fun contentDescriptionConnectingOmitsDuration() {
        val strings = SautiStrings()
        val connecting = minUiStateOf(CallState(phase = CallPhase.CONNECTING, durationMs = 65_000))

        assertEquals(strings.connecting, minimizedContentDescription(connecting, strings))
    }

    @Test
    fun contentDescriptionReflectsReconnecting() {
        val strings = SautiStrings()
        val reconnecting = minUiStateOf(
            CallState(
                phase = CallPhase.CONNECTED,
                participants = listOf(minParticipant("me"), minParticipant("peer")),
                durationMs = 65_000,
                reconnecting = true
            ),
            selfParticipantId = "me"
        )

        assertEquals(
            strings.reconnecting + " " + formatDuration(65_000),
            minimizedContentDescription(reconnecting, strings)
        )
    }

    @Test
    fun leaveRoutesThroughController() {
        val controller = MinRecordingController()
        val ui = minUiStateOf(CallState(), controller = controller)

        ui.leave()

        assertTrue(controller.left)
    }

    @Test
    fun toggleMuteRoutesThroughController() {
        val controller = MinRecordingController()
        val ui = minUiStateOf(CallState(localMuted = false), controller = controller)

        ui.toggleMute()

        assertEquals(listOf(true), controller.muted)
    }

    @Test
    fun returnToCallStringHasDefault() {
        assertEquals("Return to call", SautiStrings().returnToCall)
    }
}
