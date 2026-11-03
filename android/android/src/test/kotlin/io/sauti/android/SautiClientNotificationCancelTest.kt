package io.sauti.android

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.sauti.android.audio.AudioController
import io.sauti.android.audio.AudioDevice
import io.sauti.android.persistence.ResumeStore
import io.sauti.android.service.CallForegroundService
import io.sauti.engine.CallEvent
import io.sauti.engine.CallState
import io.sauti.engine.JoinConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertNull

private class ReconnectingEngine : CallEngine {
    private val stateFlow = MutableStateFlow(CallState())
    private val eventsFlow = MutableSharedFlow<CallEvent>(extraBufferCapacity = 16)
    override val state: StateFlow<CallState> get() = stateFlow.asStateFlow()
    override val events: SharedFlow<CallEvent> get() = eventsFlow.asSharedFlow()

    fun setReconnecting(value: Boolean) {
        stateFlow.value = stateFlow.value.copy(reconnecting = value)
    }

    override suspend fun join(config: JoinConfig) = Unit
    override fun setMuted(muted: Boolean) = Unit
    override fun setHold(onHold: Boolean) = Unit
    override fun onNetworkChanged() = Unit
    override fun leave() = Unit
    override fun dispose() = Unit
}

private class NotifyNoopAudio : AudioController {
    override val currentDevice = MutableStateFlow(AudioDevice.EARPIECE)
    override val availableDevices = MutableStateFlow(setOf(AudioDevice.EARPIECE))
    override val interrupted = MutableStateFlow(false)
    override var onInterrupted: ((Boolean) -> Unit)? = null
    override fun start() = Unit
    override fun stop() = Unit
    override fun selectDevice(device: AudioDevice) = Unit
    override fun forceInterruption(active: Boolean) = Unit
}

private class NotifyNoopStartable : Startable {
    override fun start() = Unit
    override fun stop() = Unit
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SautiClientNotificationCancelTest {

    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private val context: Context get() = app

    private val joinRequest = SautiJoinRequest(
        url = "wss://x",
        token = "t",
        roomId = "r",
        participantId = "p",
        slotGeneration = 1,
        displayTitle = "Call"
    )

    private fun build(engine: ReconnectingEngine): SautiClient = SautiClient(
        context = context,
        scope = CoroutineScope(Dispatchers.Unconfined),
        audio = NotifyNoopAudio(),
        engine = engine,
        resumeStore = ResumeStore(context),
        telephonyFactory = { _, _ -> NotifyNoopStartable() },
        connectivityFactory = { _, _ -> NotifyNoopStartable() },
        enableTelephonyAutoMute = false
    )

    private fun drainStartedServices() {
        while (shadowOf(app).nextStartedService != null) Unit
    }

    @Test
    fun disposeStopsTheCollectorFromRepostingForeground() = runBlocking {
        val engine = ReconnectingEngine()
        val client = build(engine)
        client.join(joinRequest)
        drainStartedServices()

        client.dispose()
        drainStartedServices()
        engine.setReconnecting(true)

        assertNull(shadowOf(app).peekNextStartedService())
    }

    @Test
    fun leaveStopsTheCollectorFromRepostingForeground() = runBlocking {
        val engine = ReconnectingEngine()
        val client = build(engine)
        client.join(joinRequest)
        drainStartedServices()

        client.leave()
        drainStartedServices()
        engine.setReconnecting(true)

        assertNull(shadowOf(app).peekNextStartedService())
    }
}
