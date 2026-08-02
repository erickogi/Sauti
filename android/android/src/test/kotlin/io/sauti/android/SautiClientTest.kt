package io.sauti.android

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import io.sauti.android.audio.AudioController
import io.sauti.android.audio.AudioDevice
import io.sauti.android.net.NetworkEventKind
import io.sauti.android.persistence.ResumeRecord
import io.sauti.android.persistence.ResumeStore
import io.sauti.android.telephony.InterruptionMode
import io.sauti.android.telephony.InterruptionPolicy
import io.sauti.engine.CallEvent
import io.sauti.engine.CallPhase
import io.sauti.engine.CallState
import io.sauti.engine.JoinConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSensor
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeCallEngine : CallEngine {
    private val stateFlow = MutableStateFlow(CallState())
    private val eventsFlow = MutableSharedFlow<CallEvent>(extraBufferCapacity = 16)
    override val state: StateFlow<CallState> get() = stateFlow.asStateFlow()
    override val events: SharedFlow<CallEvent> get() = eventsFlow.asSharedFlow()

    val muteCalls = mutableListOf<Boolean>()
    val holdCalls = mutableListOf<Boolean>()
    var networkChanges = 0
    var leaveCount = 0
    var lastJoin: JoinConfig? = null

    fun setPhase(phase: CallPhase) {
        stateFlow.value = stateFlow.value.copy(phase = phase)
    }

    override suspend fun join(config: JoinConfig) {
        lastJoin = config
    }
    override fun setMuted(muted: Boolean) {
        muteCalls += muted
    }

    override fun setHold(onHold: Boolean) {
        holdCalls += onHold
    }

    override fun onNetworkChanged() {
        networkChanges += 1
    }

    override fun leave() {
        leaveCount += 1
    }

    override fun dispose() = Unit
}

private class FakeAudioController : AudioController {
    override val currentDevice = MutableStateFlow(AudioDevice.EARPIECE)
    override val availableDevices = MutableStateFlow(setOf(AudioDevice.EARPIECE, AudioDevice.SPEAKER))
    override val interrupted = MutableStateFlow(false)
    override var onInterrupted: ((Boolean) -> Unit)? = null

    val forced = mutableListOf<Boolean>()
    var stopped = false

    override fun start() = Unit
    override fun stop() {
        stopped = true
    }

    override fun selectDevice(device: AudioDevice) = Unit
    override fun forceInterruption(active: Boolean) {
        forced += active
    }
}

private class FakeStartable : Startable {
    override fun start() = Unit
    override fun stop() = Unit
}

private class RecordingStartable : Startable {
    var startCount = 0
    var stopCount = 0
    override fun start() {
        startCount += 1
    }

    override fun stop() {
        stopCount += 1
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SautiClientTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun build(
        engine: FakeCallEngine = FakeCallEngine(),
        audio: FakeAudioController = FakeAudioController(),
        resumeStore: ResumeStore = ResumeStore(context),
        onConnectivity: ((NetworkEventKind) -> Unit) -> Unit = {},
        onTelephony: ((Boolean) -> Unit) -> Unit = {},
        telephony: Startable = FakeStartable(),
        enableTelephonyAutoMute: Boolean = true,
        interruptionPolicy: InterruptionPolicy = InterruptionPolicy(),
        enableProximity: Boolean = false,
        scope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined)
    ): SautiClient = SautiClient(
        context = context,
        scope = scope,
        audio = audio,
        engine = engine,
        resumeStore = resumeStore,
        telephonyFactory = { _, cb -> onTelephony(cb); telephony },
        connectivityFactory = { _, cb -> onConnectivity(cb); FakeStartable() },
        interruptionPolicy = interruptionPolicy,
        enableTelephonyAutoMute = enableTelephonyAutoMute,
        enableProximity = enableProximity
    )

    private val sensorManager: SensorManager
        get() = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val joinRequest = SautiJoinRequest(
        url = "wss://x",
        token = "t",
        roomId = "r",
        participantId = "p",
        slotGeneration = 1,
        displayTitle = "Call"
    )

    private fun installProximitySensor() {
        val sensor = ShadowSensor.newInstance(Sensor.TYPE_PROXIMITY)
        shadowOf(sensor).setMaximumRange(5f)
        shadowOf(sensorManager).addSensor(Sensor.TYPE_PROXIMITY, sensor)
        shadowOf(context.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .setIsWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, true)
    }

    @Test
    fun endWhenLastPeerLeavesDefaultsFalseInJoinConfig() = runBlocking {
        val engine = FakeCallEngine()
        val client = build(engine = engine)

        client.join(joinRequest)

        assertEquals(false, engine.lastJoin?.endWhenLastPeerLeaves)
        client.leave()
    }

    @Test
    fun endWhenLastPeerLeavesIsPlumbedToEngine() = runBlocking {
        val engine = FakeCallEngine()
        val client = build(engine = engine)

        client.join(joinRequest.copy(endWhenLastPeerLeaves = true))

        assertEquals(true, engine.lastJoin?.endWhenLastPeerLeaves)
        client.leave()
    }

    @Test
    fun proximityDisabledByDefaultRegistersNoSensor() = runBlocking {
        installProximitySensor()
        val client = build()

        client.join(joinRequest)

        assertTrue(shadowOf(sensorManager).listeners.isEmpty())
        client.leave()
    }

    @Test
    fun proximityEnabledRegistersSensorOnJoinAndUnregistersOnLeave() = runBlocking {
        installProximitySensor()
        val client = build(enableProximity = true)

        client.join(joinRequest)
        assertTrue(shadowOf(sensorManager).listeners.isNotEmpty())

        client.leave()
        assertTrue(shadowOf(sensorManager).listeners.isEmpty())
    }

    @Test
    fun audioInterruptionMutesEngineAndRestoresOnRegain() {
        val engine = FakeCallEngine()
        val audio = FakeAudioController()
        build(engine = engine, audio = audio)

        audio.onInterrupted?.invoke(true)
        assertEquals(true, engine.muteCalls.last())

        audio.onInterrupted?.invoke(false)
        assertEquals(false, engine.muteCalls.last())
    }

    @Test
    fun autoMuteRestoresPriorUserMuteIntentOnRegain() {
        val engine = FakeCallEngine()
        val audio = FakeAudioController()
        val client = build(engine = engine, audio = audio)

        client.setMuted(true)
        audio.onInterrupted?.invoke(true)
        audio.onInterrupted?.invoke(false)

        assertEquals(true, engine.muteCalls.last())
    }

    @Test
    fun defaultPolicyInterruptionMutesOnlyAndNeverHolds() {
        val engine = FakeCallEngine()
        val audio = FakeAudioController()
        build(engine = engine, audio = audio)

        audio.onInterrupted?.invoke(true)
        audio.onInterrupted?.invoke(false)

        assertEquals(listOf(true, false), engine.muteCalls)
        assertEquals(emptyList(), engine.holdCalls)
    }

    @Test
    fun holdPolicyInterruptionMutesAndHoldsThenRestoresIntent() {
        val engine = FakeCallEngine()
        val audio = FakeAudioController()
        val client = build(
            engine = engine,
            audio = audio,
            interruptionPolicy = InterruptionPolicy(InterruptionMode.Hold)
        )
        client.setMuted(true)
        client.setHold(true)

        audio.onInterrupted?.invoke(true)
        assertEquals(true, engine.muteCalls.last())
        assertEquals(true, engine.holdCalls.last())

        audio.onInterrupted?.invoke(false)
        assertEquals(true, engine.muteCalls.last())
        assertEquals(true, engine.holdCalls.last())
    }

    @Test
    fun connectivityCallbackDrivesEngineNetworkChangeWhenConnected() {
        val engine = FakeCallEngine()
        engine.setPhase(CallPhase.CONNECTED)
        var callback: ((NetworkEventKind) -> Unit)? = null
        build(engine = engine, onConnectivity = { callback = it })

        callback?.invoke(NetworkEventKind.Available)
        assertEquals(1, engine.networkChanges)
    }

    @Test
    fun connectivityCallbackIsNoOpBeforeConnected() {
        val engine = FakeCallEngine()
        var callback: ((NetworkEventKind) -> Unit)? = null
        build(engine = engine, onConnectivity = { callback = it })

        callback?.invoke(NetworkEventKind.Available)
        assertEquals(0, engine.networkChanges)
    }

    @Test
    fun telephonyCallbackForcesAudioInterruption() {
        val audio = FakeAudioController()
        var callback: ((Boolean) -> Unit)? = null
        build(audio = audio, onTelephony = { callback = it })

        callback?.invoke(true)
        assertEquals(listOf(true), audio.forced)
    }

    @Test
    fun leaveClearsResumeStoreAndStopsEngine() = runBlocking {
        val engine = FakeCallEngine()
        val store = ResumeStore(context)
        store.save(ResumeRecord(roomId = "r", participantId = "p", token = "t", url = "wss://x", slotGeneration = 1))
        val client = build(engine = engine, resumeStore = store)

        client.leave()

        withTimeout(5_000) {
            while (store.load() != null) delay(20)
        }
        assertNull(store.load())
        assertEquals(1, engine.leaveCount)
    }

    @Test
    fun clientIsSautiCallHandle() {
        val client: SautiCall = build()
        assertTrue(client is SautiClient)
    }

    @Test
    fun selfParticipantIdSetOnJoin() = runBlocking {
        val client = build()
        assertNull(client.selfParticipantId)

        client.join(joinRequest)

        assertEquals(joinRequest.participantId, client.selfParticipantId)
        client.leave()
    }

    @Test
    fun telephonyWatcherStartsOnJoinWhenAutoMuteEnabled() = runBlocking {
        val telephony = RecordingStartable()
        val client = build(telephony = telephony, enableTelephonyAutoMute = true)

        client.join(joinRequest)

        assertEquals(1, telephony.startCount)
        client.leave()
    }

    @Test
    fun telephonyWatcherNotStartedOnJoinWhenAutoMuteDisabled() = runBlocking {
        val telephony = RecordingStartable()
        val client = build(telephony = telephony, enableTelephonyAutoMute = false)

        client.join(joinRequest)

        assertEquals(0, telephony.startCount)
        client.leave()
    }

    @Test
    fun rearmTelephonyReStartsWatcherWhenEnabled() = runBlocking {
        val telephony = RecordingStartable()
        val client = build(telephony = telephony, enableTelephonyAutoMute = true)

        client.join(joinRequest)
        client.rearmTelephony()

        assertEquals(2, telephony.startCount)
        client.leave()
    }

    @Test
    fun rearmTelephonyIsNoOpWhenDisabled() = runBlocking {
        val telephony = RecordingStartable()
        val client = build(telephony = telephony, enableTelephonyAutoMute = false)

        client.join(joinRequest)
        client.rearmTelephony()

        assertEquals(0, telephony.startCount)
        client.leave()
    }

    @Test
    fun rearmTelephonyIsNoOpAfterLeave() = runBlocking {
        val telephony = RecordingStartable()
        val client = build(telephony = telephony, enableTelephonyAutoMute = true)

        client.join(joinRequest)
        client.leave()
        val afterLeave = telephony.startCount

        client.rearmTelephony()

        assertEquals(afterLeave, telephony.startCount)
    }

    @Test
    fun leaveIsIdempotent() = runBlocking {
        val engine = FakeCallEngine()
        val client = build(engine = engine)

        client.leave()
        client.leave()
        client.hangUp()

        assertEquals(1, engine.leaveCount)
    }

    @Test
    fun leaveInternalDoesNotDoubleStopEngine() = runBlocking {
        val engine = FakeCallEngine()
        val client = build(engine = engine)

        client.leaveInternal()
        client.leave()

        assertEquals(1, engine.leaveCount)
    }
}
