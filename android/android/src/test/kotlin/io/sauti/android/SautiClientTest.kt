package io.sauti.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.sauti.android.audio.AudioController
import io.sauti.android.audio.AudioDevice
import io.sauti.android.persistence.ResumeRecord
import io.sauti.android.persistence.ResumeStore
import io.sauti.engine.CallEvent
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
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

private class FakeCallEngine : CallEngine {
    private val stateFlow = MutableStateFlow(CallState())
    private val eventsFlow = MutableSharedFlow<CallEvent>(extraBufferCapacity = 16)
    override val state: StateFlow<CallState> get() = stateFlow.asStateFlow()
    override val events: SharedFlow<CallEvent> get() = eventsFlow.asSharedFlow()

    val muteCalls = mutableListOf<Boolean>()
    var networkChanges = 0
    var leaveCount = 0

    override suspend fun join(config: JoinConfig) = Unit
    override fun setMuted(muted: Boolean) {
        muteCalls += muted
    }

    override fun setHold(onHold: Boolean) = Unit
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SautiClientTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun build(
        engine: FakeCallEngine = FakeCallEngine(),
        audio: FakeAudioController = FakeAudioController(),
        resumeStore: ResumeStore = ResumeStore(context),
        onConnectivity: (() -> Unit) -> Unit = {},
        onTelephony: ((Boolean) -> Unit) -> Unit = {},
        scope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined)
    ): SautiClient = SautiClient(
        context = context,
        scope = scope,
        audio = audio,
        engine = engine,
        resumeStore = resumeStore,
        telephonyFactory = { _, cb -> onTelephony(cb); FakeStartable() },
        connectivityFactory = { _, cb -> onConnectivity(cb); FakeStartable() }
    )

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
    fun connectivityCallbackDrivesEngineNetworkChange() {
        val engine = FakeCallEngine()
        var callback: (() -> Unit)? = null
        build(engine = engine, onConnectivity = { callback = it })

        callback?.invoke()
        assertEquals(1, engine.networkChanges)
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
}
