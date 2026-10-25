package io.sauti.android

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import io.sauti.android.audio.AudioDevice
import io.sauti.android.persistence.ResumeStore
import io.sauti.android.service.CallForegroundService
import io.sauti.android.service.CallNotification
import io.sauti.android.service.CallPresence
import io.sauti.android.service.EndReason
import io.sauti.android.service.SautiCallIntents
import io.sauti.engine.CallEvent
import io.sauti.engine.CallState
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
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class GuardRecordingEngine : CallEngine {
    private val stateFlow = MutableStateFlow(CallState())
    private val eventsFlow = MutableSharedFlow<CallEvent>(extraBufferCapacity = 16)
    override val state: StateFlow<CallState> get() = stateFlow.asStateFlow()
    override val events: SharedFlow<CallEvent> get() = eventsFlow.asSharedFlow()
    override suspend fun join(config: io.sauti.engine.JoinConfig) = Unit
    override fun setMuted(muted: Boolean) = Unit
    override fun setHold(onHold: Boolean) = Unit
    override fun onNetworkChanged() = Unit
    override fun leave() = Unit
    override fun dispose() = Unit
}

private class GuardNoopAudio : io.sauti.android.audio.AudioController {
    override val currentDevice = MutableStateFlow(AudioDevice.EARPIECE)
    override val availableDevices = MutableStateFlow(setOf(AudioDevice.EARPIECE))
    override val interrupted = MutableStateFlow(false)
    override var onInterrupted: ((Boolean) -> Unit)? = null
    override fun start() = Unit
    override fun stop() = Unit
    override fun selectDevice(device: AudioDevice) = Unit
    override fun forceInterruption(active: Boolean) = Unit
}

private class GuardNoopStartable : Startable {
    override fun start() = Unit
    override fun stop() = Unit
}

@Implements(Service::class)
class SecurityThrowingShadowService {
    var stopSelfCalled = false

    @Implementation
    fun startForeground(id: Int, notification: Notification, foregroundServiceType: Int) {
        throw SecurityException("mic foreground start blocked")
    }

    @Implementation
    fun startForeground(id: Int, notification: Notification) {
        throw SecurityException("mic foreground start blocked")
    }

    @Implementation
    fun stopForeground(flags: Int) = Unit

    @Implementation
    fun stopForeground(removeNotification: Boolean) = Unit

    @Implementation
    fun stopSelf() {
        stopSelfCalled = true
    }
}

@Implements(Service::class)
class NotAllowedThrowingShadowService {
    var stopSelfCalled = false

    @Implementation
    fun startForeground(id: Int, notification: Notification, foregroundServiceType: Int) {
        throw ForegroundServiceStartNotAllowedException("background fgs start not allowed")
    }

    @Implementation
    fun startForeground(id: Int, notification: Notification) {
        throw ForegroundServiceStartNotAllowedException("background fgs start not allowed")
    }

    @Implementation
    fun stopForeground(flags: Int) = Unit

    @Implementation
    fun stopForeground(removeNotification: Boolean) = Unit

    @Implementation
    fun stopSelf() {
        stopSelfCalled = true
    }
}

@Implements(Service::class)
class PlainIseThrowingShadowService {
    var stopSelfCalled = false

    @Implementation
    fun startForeground(id: Int, notification: Notification, foregroundServiceType: Int) {
        throw IllegalStateException("unrelated illegal state")
    }

    @Implementation
    fun startForeground(id: Int, notification: Notification) {
        throw IllegalStateException("unrelated illegal state")
    }

    @Implementation
    fun stopForeground(flags: Int) = Unit

    @Implementation
    fun stopForeground(removeNotification: Boolean) = Unit

    @Implementation
    fun stopSelf() {
        stopSelfCalled = true
    }
}

private object StartGuardSupport {

    val request = SautiJoinRequest(
        url = "wss://x",
        token = "t",
        roomId = "r",
        participantId = "p",
        slotGeneration = 1,
        displayTitle = "Call"
    )

    fun buildClient(context: Context): SautiClient = SautiClient(
        context = context,
        scope = CoroutineScope(Dispatchers.Unconfined),
        audio = GuardNoopAudio(),
        engine = GuardRecordingEngine(),
        resumeStore = ResumeStore(context),
        telephonyFactory = { _, _ -> GuardNoopStartable() },
        connectivityFactory = { _, _ -> GuardNoopStartable() },
        enableTelephonyAutoMute = true
    )

    fun connectingIntent(context: Context) = Intent(context, CallForegroundService::class.java).apply {
        putExtra(CallForegroundService.EXTRA_TITLE, "Session")
        putExtra(CallForegroundService.EXTRA_PRESENCE, CallPresence.CONNECTING.name)
    }

    fun adoptIntent(context: Context) = Intent(context, CallForegroundService::class.java).apply {
        action = CallForegroundService.ACTION_ADOPT
    }

    fun stopIntent(context: Context) = Intent(context, CallForegroundService::class.java).apply {
        action = CallForegroundService.ACTION_STOP
    }

    fun stopAdoptedIntent(context: Context) = Intent(context, CallForegroundService::class.java).apply {
        action = CallForegroundService.ACTION_STOP_ADOPTED
    }

    fun intents(context: Context) = SautiCallIntents(
        CallNotification.placeholderIntent(context),
        CallNotification.hangupIntent(context)
    )
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], shadows = [SecurityThrowingShadowService::class])
class CallForegroundServiceSecurityGuardTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun plainStartThatThrowsStopsServiceWithoutCrashing() {
        val intent = StartGuardSupport.connectingIntent(context)
        val service = Robolectric.buildService(CallForegroundService::class.java, intent).create().get()

        service.onStartCommand(intent, 0, 1)

        val shadow = Shadow.extract<SecurityThrowingShadowService>(service)
        assertTrue(shadow.stopSelfCalled)
    }

    @Test
    fun adoptThatThrowsSurfacesFailureAndClearsCall() {
        val client = StartGuardSupport.buildClient(context)
        CallForegroundService.startCall(
            context, client, StartGuardSupport.request, StartGuardSupport.intents(context),
            enableTelephonyAutoMute = true
        )
        val intent = StartGuardSupport.adoptIntent(context)
        val service = Robolectric.buildService(CallForegroundService::class.java, intent).create().get()

        service.onStartCommand(intent, 0, 1)

        assertEquals(EndReason.FAILED, CallForegroundService.endedReason.value)
        assertNull(CallForegroundService.call.value)
        assertTrue(Shadow.extract<SecurityThrowingShadowService>(service).stopSelfCalled)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], shadows = [NotAllowedThrowingShadowService::class])
class CallForegroundServiceNotAllowedGuardTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun plainStartThatThrowsStopsServiceWithoutCrashing() {
        val intent = StartGuardSupport.connectingIntent(context)
        val service = Robolectric.buildService(CallForegroundService::class.java, intent).create().get()

        service.onStartCommand(intent, 0, 1)

        val shadow = Shadow.extract<NotAllowedThrowingShadowService>(service)
        assertTrue(shadow.stopSelfCalled)
    }

    @Test
    fun adoptThatThrowsSurfacesFailureAndClearsCall() {
        val client = StartGuardSupport.buildClient(context)
        CallForegroundService.startCall(
            context, client, StartGuardSupport.request, StartGuardSupport.intents(context),
            enableTelephonyAutoMute = true
        )
        val intent = StartGuardSupport.adoptIntent(context)
        val service = Robolectric.buildService(CallForegroundService::class.java, intent).create().get()

        service.onStartCommand(intent, 0, 1)

        assertEquals(EndReason.FAILED, CallForegroundService.endedReason.value)
        assertNull(CallForegroundService.call.value)
        assertTrue(Shadow.extract<NotAllowedThrowingShadowService>(service).stopSelfCalled)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], shadows = [PlainIseThrowingShadowService::class])
class CallForegroundServicePlainIseRethrowTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun plainIllegalStateBelowApi31IsRethrownAndNotResolvedAsNotAllowed() {
        val intent = StartGuardSupport.connectingIntent(context)
        val service = Robolectric.buildService(CallForegroundService::class.java, intent).create().get()

        assertFailsWith<IllegalStateException> {
            service.onStartCommand(intent, 0, 1)
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CallForegroundServiceAdoptedTeardownTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun stopAdoptedTearsDownAnAdoptedClient() {
        CallForegroundService.clearConnectingCanceller()
        val client = StartGuardSupport.buildClient(context)
        CallForegroundService.startCall(
            context, client, StartGuardSupport.request, StartGuardSupport.intents(context),
            enableTelephonyAutoMute = true
        )
        val adoptIntent = StartGuardSupport.adoptIntent(context)
        val service = Robolectric.buildService(CallForegroundService::class.java, adoptIntent).create().get()
        service.onStartCommand(adoptIntent, 0, 1)
        assertNotNull(CallForegroundService.call.value)

        service.onStartCommand(StartGuardSupport.stopAdoptedIntent(context), 0, 2)

        assertNull(CallForegroundService.call.value)
    }

    @Test
    fun stopAdoptedWithoutAClientDoesNotInvokeTheConnectingCanceller() {
        var cancelled = false
        CallForegroundService.armConnectingCanceller { cancelled = true }

        val stopAdopted = StartGuardSupport.stopAdoptedIntent(context)
        val service = Robolectric.buildService(CallForegroundService::class.java, stopAdopted).create().get()
        service.onStartCommand(stopAdopted, 0, 1)

        assertFalse(cancelled)
        CallForegroundService.clearConnectingCanceller()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CallForegroundServiceConnectingCancellerTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun stopInConnectingStateInvokesArmedCancellerExactlyOnce() {
        var cancelCount = 0
        CallForegroundService.armConnectingCanceller { cancelCount += 1 }

        val startIntent = StartGuardSupport.connectingIntent(context)
        val service = Robolectric.buildService(CallForegroundService::class.java, startIntent).create().get()
        service.onStartCommand(startIntent, 0, 1)

        val stopIntent = StartGuardSupport.stopIntent(context)
        service.onStartCommand(stopIntent, 0, 2)
        service.onStartCommand(stopIntent, 0, 3)

        assertEquals(1, cancelCount)
    }

    @Test
    fun adoptClearsCancellerSoStopDoesNotInvokeIt() {
        var cancelled = false
        CallForegroundService.armConnectingCanceller { cancelled = true }

        val client = StartGuardSupport.buildClient(context)
        CallForegroundService.startCall(
            context, client, StartGuardSupport.request, StartGuardSupport.intents(context),
            enableTelephonyAutoMute = true
        )
        val adoptIntent = StartGuardSupport.adoptIntent(context)
        val service = Robolectric.buildService(CallForegroundService::class.java, adoptIntent).create().get()
        service.onStartCommand(adoptIntent, 0, 1)

        service.onStartCommand(StartGuardSupport.stopIntent(context), 0, 2)

        assertFalse(cancelled)
    }
}
