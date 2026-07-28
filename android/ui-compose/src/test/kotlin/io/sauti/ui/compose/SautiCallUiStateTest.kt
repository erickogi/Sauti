package io.sauti.ui.compose

import io.sauti.android.audio.AudioDevice
import io.sauti.engine.CallPhase
import io.sauti.engine.CallState
import io.sauti.engine.ConnectionState
import io.sauti.engine.ParticipantSnapshot
import io.sauti.engine.Quality
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class RecordingController : SautiCallController {
    val muted = mutableListOf<Boolean>()
    val holds = mutableListOf<Boolean>()
    val devices = mutableListOf<AudioDevice>()
    var left = false

    override fun setMuted(muted: Boolean) {
        this.muted.add(muted)
    }

    override fun setHold(onHold: Boolean) {
        holds.add(onHold)
    }

    override fun selectDevice(device: AudioDevice) {
        devices.add(device)
    }

    override fun leave() {
        left = true
    }
}

private fun participant(
    id: String,
    name: String? = null,
    muted: Boolean = false,
    onHold: Boolean = false,
    connectionState: ConnectionState = ConnectionState.CONNECTED,
    quality: Quality = Quality.GOOD
): ParticipantSnapshot = ParticipantSnapshot(
    participantId = id,
    metadata = if (name == null) emptyMap() else mapOf("name" to JsonPrimitive(name)),
    muted = muted,
    onHold = onHold,
    connectionState = connectionState,
    quality = quality
)

private fun row(
    id: String = "peer",
    label: String = "Peer",
    isSelf: Boolean = false,
    muted: Boolean = false,
    onHold: Boolean = false,
    connectionState: ConnectionState = ConnectionState.CONNECTED,
    quality: Quality = Quality.GOOD
): SautiParticipantRow = SautiParticipantRow(
    participantId = id,
    label = label,
    isSelf = isSelf,
    muted = muted,
    onHold = onHold,
    connectionState = connectionState,
    quality = quality
)

private fun uiStateOf(
    state: CallState,
    selfParticipantId: String? = null,
    currentDevice: AudioDevice = AudioDevice.EARPIECE,
    availableDevices: Set<AudioDevice> = setOf(AudioDevice.EARPIECE, AudioDevice.SPEAKER),
    interrupted: Boolean = false,
    controller: SautiCallController = RecordingController()
): SautiCallUiState = buildSautiCallUiState(
    state = state,
    currentDevice = currentDevice,
    availableDevices = availableDevices,
    interrupted = interrupted,
    selfParticipantId = selfParticipantId,
    controller = controller
)

class SautiCallUiStateTest {

    @Test
    fun selfIsOrderedFirst() {
        val state = CallState(
            phase = CallPhase.CONNECTED,
            participants = listOf(
                participant("peer-a"),
                participant("me"),
                participant("peer-b")
            )
        )
        val ui = uiStateOf(state, selfParticipantId = "me")

        assertEquals("me", ui.roster.first().participantId)
        assertTrue(ui.roster.first().isSelf)
        assertEquals(listOf("peer-a", "peer-b"), ui.others.map { it.participantId })
        assertEquals("me", ui.orderedParticipants.first().participantId)
    }

    @Test
    fun rosterKeepsOrderWithoutSelf() {
        val state = CallState(
            phase = CallPhase.CONNECTED,
            participants = listOf(participant("peer-a"), participant("peer-b"))
        )
        val ui = uiStateOf(state, selfParticipantId = null)

        assertEquals(listOf("peer-a", "peer-b"), ui.roster.map { it.participantId })
        assertFalse(ui.roster.any { it.isSelf })
        assertEquals(null, ui.self)
    }

    @Test
    fun muteReflectsLocalMuted() {
        val muted = uiStateOf(CallState(localMuted = true))
        val open = uiStateOf(CallState(localMuted = false))

        assertTrue(muted.localMuted)
        assertFalse(open.localMuted)
    }

    @Test
    fun labelUsesMetadataNameThenShortId() {
        val named = participant("peer-1234567890", name = "Alex")
        val anonymous = participant("peer-1234567890")

        assertEquals("Alex", participantLabel(named))
        assertEquals("peer-123", participantLabel(anonymous))
    }

    @Test
    fun toggleMuteCallsThroughToController() {
        val controller = RecordingController()
        val ui = uiStateOf(CallState(localMuted = false), controller = controller)

        ui.toggleMute()

        assertEquals(listOf(true), controller.muted)
    }

    @Test
    fun deviceSelectCallsThrough() {
        val controller = RecordingController()
        val ui = uiStateOf(CallState(), controller = controller)

        ui.selectDevice(AudioDevice.SPEAKER)
        ui.setHold(true)
        ui.leave()

        assertEquals(listOf(AudioDevice.SPEAKER), controller.devices)
        assertEquals(listOf(true), controller.holds)
        assertTrue(controller.left)
    }

    @Test
    fun statusTextFollowsPhaseAndRoster() {
        val strings = SautiStrings()
        val connecting = uiStateOf(CallState(phase = CallPhase.CONNECTING))
        val ended = uiStateOf(CallState(phase = CallPhase.LEFT))
        val waiting = uiStateOf(
            CallState(phase = CallPhase.CONNECTED, participants = listOf(participant("me"))),
            selfParticipantId = "me"
        )
        val active = uiStateOf(
            CallState(
                phase = CallPhase.CONNECTED,
                participants = listOf(participant("me"), participant("peer"))
            ),
            selfParticipantId = "me"
        )
        val reconnecting = uiStateOf(
            CallState(
                phase = CallPhase.CONNECTED,
                participants = listOf(participant("me"), participant("peer")),
                reconnecting = true
            ),
            selfParticipantId = "me"
        )

        assertEquals(strings.connecting, callStatusText(connecting, strings))
        assertEquals(strings.ended, callStatusText(ended, strings))
        assertEquals(strings.waitingForPeer, callStatusText(waiting, strings))
        assertEquals(strings.inCall, callStatusText(active, strings))
        assertEquals(strings.reconnecting, callStatusText(reconnecting, strings))
    }

    @Test
    fun durationFormatsAsMinutesAndSeconds() {
        assertEquals("00:00", formatDuration(0))
        assertEquals("01:05", formatDuration(65_000))
        assertEquals("10:00", formatDuration(600_000))
    }

    @Test
    fun audioDeviceOrderFiltersAndSorts() {
        val ordered = audioDeviceOrder(setOf(AudioDevice.SPEAKER, AudioDevice.EARPIECE))

        assertEquals(listOf(AudioDevice.EARPIECE, AudioDevice.SPEAKER), ordered)
    }

    @Test
    fun interruptedPassesThrough() {
        val ui = uiStateOf(CallState(), interrupted = true)

        assertTrue(ui.interrupted)
    }

    @Test
    fun isReconnectingReducesReconnectingAndUnreachable() {
        assertTrue(isReconnecting(ConnectionState.RECONNECTING))
        assertTrue(isReconnecting(ConnectionState.UNREACHABLE))
        assertFalse(isReconnecting(ConnectionState.CONNECTED))
        assertFalse(isReconnecting(ConnectionState.CONNECTING))
        assertFalse(isReconnecting(ConnectionState.LEFT))
    }

    @Test
    fun defaultReconnectingStringUsesEllipsis() {
        assertEquals("Reconnecting…", SautiStrings().reconnecting)
    }

    @Test
    fun participantStatusTextAppendsReconnecting() {
        val strings = SautiStrings()
        val reconnecting = row(connectionState = ConnectionState.RECONNECTING)
        val unreachable = row(connectionState = ConnectionState.UNREACHABLE)
        val connected = row(connectionState = ConnectionState.CONNECTED)
        val connecting = row(connectionState = ConnectionState.CONNECTING)
        val left = row(connectionState = ConnectionState.LEFT)
        val mutedReconnecting = row(muted = true, connectionState = ConnectionState.RECONNECTING)

        assertEquals(strings.reconnecting, participantStatusText(reconnecting, strings))
        assertEquals(strings.reconnecting, participantStatusText(unreachable, strings))
        assertEquals("", participantStatusText(connected, strings))
        assertEquals("", participantStatusText(connecting, strings))
        assertEquals("", participantStatusText(left, strings))
        assertEquals(
            strings.muted + ", " + strings.reconnecting,
            participantStatusText(mutedReconnecting, strings)
        )
    }

    @Test
    fun peerReconnectingIsRemoteOnly() {
        val remote = uiStateOf(
            CallState(
                phase = CallPhase.CONNECTED,
                participants = listOf(
                    participant("me"),
                    participant("peer", connectionState = ConnectionState.RECONNECTING)
                )
            ),
            selfParticipantId = "me"
        )
        val remoteUnreachable = uiStateOf(
            CallState(
                phase = CallPhase.CONNECTED,
                participants = listOf(
                    participant("me"),
                    participant("peer", connectionState = ConnectionState.UNREACHABLE)
                )
            ),
            selfParticipantId = "me"
        )
        val selfOnly = uiStateOf(
            CallState(
                phase = CallPhase.CONNECTED,
                participants = listOf(
                    participant("me", connectionState = ConnectionState.RECONNECTING),
                    participant("peer")
                )
            ),
            selfParticipantId = "me"
        )
        val allSettled = uiStateOf(
            CallState(
                phase = CallPhase.CONNECTED,
                participants = listOf(
                    participant("me"),
                    participant("peer-a", connectionState = ConnectionState.CONNECTED),
                    participant("peer-b", connectionState = ConnectionState.CONNECTING),
                    participant("peer-c", connectionState = ConnectionState.LEFT)
                )
            ),
            selfParticipantId = "me"
        )

        assertTrue(remote.peerReconnecting)
        assertTrue(remoteUnreachable.peerReconnecting)
        assertFalse(selfOnly.peerReconnecting)
        assertFalse(allSettled.peerReconnecting)
    }

    @Test
    fun callStatusTextReflectsRemotePeerReconnecting() {
        val strings = SautiStrings()
        val peerReconnecting = uiStateOf(
            CallState(
                phase = CallPhase.CONNECTED,
                participants = listOf(
                    participant("me"),
                    participant("peer", connectionState = ConnectionState.RECONNECTING)
                ),
                reconnecting = false
            ),
            selfParticipantId = "me"
        )
        val peerUnreachable = uiStateOf(
            CallState(
                phase = CallPhase.CONNECTED,
                participants = listOf(
                    participant("me"),
                    participant("peer", connectionState = ConnectionState.UNREACHABLE)
                ),
                reconnecting = false
            ),
            selfParticipantId = "me"
        )
        val selfOnly = uiStateOf(
            CallState(
                phase = CallPhase.CONNECTED,
                participants = listOf(
                    participant("me", connectionState = ConnectionState.RECONNECTING),
                    participant("peer")
                ),
                reconnecting = false
            ),
            selfParticipantId = "me"
        )

        assertEquals(strings.reconnecting, callStatusText(peerReconnecting, strings))
        assertEquals(strings.reconnecting, callStatusText(peerUnreachable, strings))
        assertEquals(strings.inCall, callStatusText(selfOnly, strings))
    }
}
