package io.sauti.android

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import io.sauti.android.audio.AudioDevice
import io.sauti.android.persistence.ResumeStore
import io.sauti.android.service.CallForegroundService
import io.sauti.android.service.CallNotification
import io.sauti.android.service.EndReason
import io.sauti.android.service.SautiCallIntents
import io.sauti.engine.CallEvent
import io.sauti.engine.CallPhase
import io.sauti.engine.CallState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

private class RecordingEngine : CallEngine {
    private val stateFlow = MutableStateFlow(CallState())
    private val eventsFlow = MutableSharedFlow<CallEvent>(extraBufferCapacity = 16)
    override val state: StateFlow<CallState> get() = stateFlow.asStateFlow()
    override val events: SharedFlow<CallEvent> get() = eventsFlow.asSharedFlow()

    fun setPhase(phase: CallPhase) {
        stateFlow.value = stateFlow.value.copy(phase = phase)
    }

    override suspend fun join(config: io.sauti.engine.JoinConfig) = Unit
    override fun setMuted(muted: Boolean) = Unit
    override fun setHold(onHold: Boolean) = Unit
    override fun onNetworkChanged() = Unit
    override fun leave() = Unit
    override fun dispose() = Unit
}

private class NoopAudio : io.sauti.android.audio.AudioController {
    override val currentDevice = MutableStateFlow(AudioDevice.EARPIECE)
    override val availableDevices = MutableStateFlow(setOf(AudioDevice.EARPIECE))
    override val interrupted = MutableStateFlow(false)
    override var onInterrupted: ((Boolean) -> Unit)? = null
    override fun start() = Unit
    override fun stop() = Unit
    override fun selectDevice(device: AudioDevice) = Unit
    override fun forceInterruption(active: Boolean) = Unit
}

private class NoopStartable : Startable {
    override fun start() = Unit
    override fun stop() = Unit
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CallForegroundServiceEndReasonTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val request = SautiJoinRequest(
        url = "wss://x",
        token = "t",
        roomId = "r",
        participantId = "p",
        slotGeneration = 1,
        displayTitle = "Call"
    )

    private fun buildClient(engine: RecordingEngine): SautiClient = SautiClient(
        context = context,
        scope = CoroutineScope(Dispatchers.Unconfined),
        audio = NoopAudio(),
        engine = engine,
        resumeStore = ResumeStore(context),
        telephonyFactory = { _, _ -> NoopStartable() },
        connectivityFactory = { _, _ -> NoopStartable() }
    )

    private fun adopt(client: SautiClient) {
        val intents = SautiCallIntents(
            CallNotification.placeholderIntent(context),
            CallNotification.hangupIntent(context)
        )
        CallForegroundService.startCall(context, client, request, intents)
        val intent = Intent(context, CallForegroundService::class.java).apply {
            action = CallForegroundService.ACTION_ADOPT
        }
        val service = Robolectric.buildService(CallForegroundService::class.java, intent).create().get()
        service.onStartCommand(intent, 0, 1)
    }

    @Test
    fun endedReasonStartsNullThenCompletedWhenCallConnectsThenLeaves() {
        val engine = RecordingEngine()
        val client = buildClient(engine)

        adopt(client)
        assertNull(CallForegroundService.endedReason.value)
        assertEquals(client, CallForegroundService.call.value)

        engine.setPhase(CallPhase.CONNECTING)
        engine.setPhase(CallPhase.CONNECTED)
        engine.setPhase(CallPhase.LEFT)

        assertEquals(EndReason.COMPLETED, CallForegroundService.endedReason.value)
        assertNull(CallForegroundService.call.value)
    }

    @Test
    fun endedReasonFailedWhenCallLeavesWithoutConnecting() {
        val engine = RecordingEngine()
        val client = buildClient(engine)

        adopt(client)

        engine.setPhase(CallPhase.CONNECTING)
        engine.setPhase(CallPhase.LEFT)

        assertEquals(EndReason.FAILED, CallForegroundService.endedReason.value)
        assertNull(CallForegroundService.call.value)
    }

    @Test
    fun endedReasonResetsToNullOnNextCall() {
        val first = RecordingEngine()
        val firstClient = buildClient(first)
        adopt(firstClient)
        first.setPhase(CallPhase.CONNECTED)
        first.setPhase(CallPhase.LEFT)
        assertEquals(EndReason.COMPLETED, CallForegroundService.endedReason.value)

        val second = RecordingEngine()
        val secondClient = buildClient(second)
        adopt(secondClient)

        assertNull(CallForegroundService.endedReason.value)
        assertEquals(secondClient, CallForegroundService.call.value)
    }

    @Test
    fun endReasonIsSetBeforeCallIsNulled() {
        val engine = RecordingEngine()
        val client = buildClient(engine)
        adopt(client)
        engine.setPhase(CallPhase.CONNECTED)

        var reasonWhenCallCleared: EndReason? = null
        val observer = CoroutineScope(Dispatchers.Unconfined)
        var seenNonNull = false
        observer.launch {
            CallForegroundService.call.collect { current ->
                if (current != null) {
                    seenNonNull = true
                } else if (seenNonNull) {
                    reasonWhenCallCleared = CallForegroundService.endedReason.value
                }
            }
        }

        engine.setPhase(CallPhase.LEFT)

        assertEquals(EndReason.COMPLETED, reasonWhenCallCleared)
    }

    @Test
    fun decideEndReasonMapsConnectedFlagToReason() {
        assertEquals(EndReason.COMPLETED, CallForegroundService.decideEndReason(true))
        assertEquals(EndReason.FAILED, CallForegroundService.decideEndReason(false))
    }
}
