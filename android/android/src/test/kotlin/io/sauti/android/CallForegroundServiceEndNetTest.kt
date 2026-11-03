package io.sauti.android

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import io.sauti.android.audio.AudioController
import io.sauti.android.audio.AudioDevice
import io.sauti.android.persistence.ResumeStore
import io.sauti.android.service.CallForegroundService
import io.sauti.android.service.CallNotification
import io.sauti.android.service.CallPresence
import io.sauti.android.service.SautiCallIntents
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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import kotlin.test.assertTrue

private class LeaveThrowingEngine : CallEngine {
    var disposed = false
    private val stateFlow = MutableStateFlow(CallState())
    private val eventsFlow = MutableSharedFlow<CallEvent>(extraBufferCapacity = 16)
    override val state: StateFlow<CallState> get() = stateFlow.asStateFlow()
    override val events: SharedFlow<CallEvent> get() = eventsFlow.asSharedFlow()
    override suspend fun join(config: JoinConfig) = Unit
    override fun setMuted(muted: Boolean) = Unit
    override fun setHold(onHold: Boolean) = Unit
    override fun onNetworkChanged() = Unit
    override fun leave(): Unit = throw RuntimeException("engine leave boom")
    override fun dispose() {
        disposed = true
    }
}

private class EndNetStartable : Startable {
    var stopped = false
    override fun start() = Unit
    override fun stop() {
        stopped = true
    }
}

private class EndNetAudio : AudioController {
    var stopped = false
    override val currentDevice = MutableStateFlow(AudioDevice.EARPIECE)
    override val availableDevices = MutableStateFlow(setOf(AudioDevice.EARPIECE))
    override val interrupted = MutableStateFlow(false)
    override var onInterrupted: ((Boolean) -> Unit)? = null
    override fun start() = Unit
    override fun stop() {
        stopped = true
    }
    override fun selectDevice(device: AudioDevice) = Unit
    override fun forceInterruption(active: Boolean) = Unit
}

@Implements(Service::class)
class EndNetShadowService {
    var stopSelfCalled = false

    @Implementation
    fun startForeground(id: Int, notification: Notification, foregroundServiceType: Int) = Unit

    @Implementation
    fun startForeground(id: Int, notification: Notification) = Unit

    @Implementation
    fun stopForeground(flags: Int) = Unit

    @Implementation
    fun stopForeground(removeNotification: Boolean) = Unit

    @Implementation
    fun stopSelf() {
        stopSelfCalled = true
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], shadows = [EndNetShadowService::class])
class CallForegroundServiceEndNetTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val request = SautiJoinRequest(
        url = "wss://x",
        token = "t",
        roomId = "r",
        participantId = "p",
        slotGeneration = 1,
        displayTitle = "Call"
    )

    @Test
    fun endCallReleasesEveryResourceAndStopsEvenWhenLeaveThrows() {
        CallForegroundService.clearConnectingCanceller()
        val engine = LeaveThrowingEngine()
        val audio = EndNetAudio()
        val telephony = EndNetStartable()
        val connectivity = EndNetStartable()
        val client = SautiClient(
            context = context,
            scope = CoroutineScope(Dispatchers.Unconfined),
            audio = audio,
            engine = engine,
            resumeStore = ResumeStore(context),
            telephonyFactory = { _, _ -> telephony },
            connectivityFactory = { _, _ -> connectivity },
            enableTelephonyAutoMute = true
        )
        CallForegroundService.startCall(
            context, client, request,
            SautiCallIntents(
                CallNotification.placeholderIntent(context),
                CallNotification.hangupIntent(context)
            ),
            enableTelephonyAutoMute = true
        )
        val adoptIntent = Intent(context, CallForegroundService::class.java).apply {
            action = CallForegroundService.ACTION_ADOPT
        }
        val service = Robolectric.buildService(CallForegroundService::class.java, adoptIntent).create().get()
        service.onStartCommand(adoptIntent, 0, 1)

        val stopIntent = Intent(context, CallForegroundService::class.java).apply {
            action = CallForegroundService.ACTION_STOP
        }
        service.onStartCommand(stopIntent, 0, 2)

        assertTrue(telephony.stopped, "telephony was not stopped")
        assertTrue(connectivity.stopped, "connectivity was not stopped")
        assertTrue(audio.stopped, "audio focus was not released")
        assertTrue(engine.disposed, "engine was not disposed")
        assertTrue(Shadow.extract<EndNetShadowService>(service).stopSelfCalled, "service did not stop")
    }
}
