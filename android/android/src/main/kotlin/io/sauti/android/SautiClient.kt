package io.sauti.android

import android.content.Context
import io.sauti.android.audio.AudioDevice
import io.sauti.android.audio.AudioController
import io.sauti.android.audio.AudioSessionCoordinator
import io.sauti.android.net.ConnectivityPolicy
import io.sauti.android.net.ConnectivityWatcher
import io.sauti.android.net.NetworkChangeGate
import io.sauti.android.net.NetworkEventKind
import io.sauti.android.persistence.ResumeRecord
import io.sauti.android.persistence.ResumeStore
import io.sauti.android.service.CallForegroundService
import io.sauti.android.service.CallPresence
import io.sauti.android.telephony.InterruptionPolicy
import io.sauti.android.telephony.InterruptionReducer
import io.sauti.android.telephony.TelephonyWatcher
import io.sauti.android.telephony.UserAudioIntent
import io.sauti.engine.CallEvent
import io.sauti.engine.CallState
import io.sauti.engine.EngineConfig
import io.sauti.engine.JoinConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class SautiJoinRequest(
    val url: String,
    val token: String,
    val roomId: String,
    val participantId: String,
    val slotGeneration: Long,
    val displayTitle: String
)

class SautiClient internal constructor(
    context: Context,
    private val scope: CoroutineScope,
    private val audio: AudioController,
    private val engine: CallEngine,
    private val resumeStore: ResumeStore,
    telephonyFactory: (Context, (Boolean) -> Unit) -> Startable,
    connectivityFactory: (Context, (NetworkEventKind) -> Unit) -> Startable,
    connectivityPolicy: ConnectivityPolicy = ConnectivityPolicy(),
    private val interruptionPolicy: InterruptionPolicy = InterruptionPolicy()
) {
    private val appContext = context.applicationContext

    private val telephony = telephonyFactory(appContext) { active ->
        audio.forceInterruption(active)
    }
    private val networkGate = NetworkChangeGate(
        policy = connectivityPolicy,
        phase = { engine.state.value.phase },
        onRestart = { engine.onNetworkChanged() }
    )
    private val connectivity = connectivityFactory(appContext) { kind ->
        networkGate.onEvent(kind)
    }

    private var userMuted = false
    private var userHeld = false

    val state: StateFlow<CallState> get() = engine.state
    val events: SharedFlow<CallEvent> get() = engine.events
    val currentDevice: StateFlow<AudioDevice> get() = audio.currentDevice
    val availableDevices: StateFlow<Set<AudioDevice>> get() = audio.availableDevices
    val interrupted: StateFlow<Boolean> get() = audio.interrupted

    init {
        audio.onInterrupted = { active ->
            val effect = InterruptionReducer.reduce(
                active,
                UserAudioIntent(userMuted, userHeld),
                interruptionPolicy
            )
            engine.setMuted(effect.muted)
            effect.onHold?.let { engine.setHold(it) }
        }
    }

    constructor(
        context: Context,
        engineConfig: EngineConfig = EngineConfig(),
        scope: CoroutineScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob()),
        connectivityPolicy: ConnectivityPolicy = ConnectivityPolicy(),
        interruptionPolicy: InterruptionPolicy = InterruptionPolicy()
    ) : this(
        context = context,
        scope = scope,
        audio = AudioSessionCoordinator(context.applicationContext),
        engine = WebRtcCallEngine(context.applicationContext, engineConfig, scope),
        resumeStore = ResumeStore(context.applicationContext),
        telephonyFactory = { ctx, cb -> TelephonyWatcher(ctx, cb) },
        connectivityFactory = { ctx, cb -> ConnectivityWatcher(ctx, cb) },
        connectivityPolicy = connectivityPolicy,
        interruptionPolicy = interruptionPolicy
    )

    suspend fun join(request: SautiJoinRequest) {
        CallForegroundService.start(appContext, request.displayTitle, CallPresence.CONNECTING)
        audio.start()
        telephony.start()
        connectivity.start()
        resumeStore.save(
            ResumeRecord(
                roomId = request.roomId,
                participantId = request.participantId,
                token = request.token,
                url = request.url,
                slotGeneration = request.slotGeneration
            )
        )
        scope.launch { observeStateForNotification(request.displayTitle) }
        engine.join(JoinConfig(request.url, request.token))
    }

    private suspend fun observeStateForNotification(title: String) {
        engine.state
            .map { presenceOf(it) }
            .distinctUntilChanged()
            .collect { presence ->
                CallForegroundService.start(appContext, title, presence)
            }
    }

    private fun presenceOf(snapshot: CallState): CallPresence = when {
        snapshot.reconnecting -> CallPresence.RECONNECTING
        else -> CallPresence.ONGOING
    }

    fun setMuted(muted: Boolean) {
        userMuted = muted
        engine.setMuted(muted)
    }

    fun setHold(onHold: Boolean) {
        userHeld = onHold
        engine.setHold(onHold)
    }

    fun selectDevice(device: AudioDevice) = audio.selectDevice(device)

    fun leave() {
        engine.leave()
        telephony.stop()
        connectivity.stop()
        audio.stop()
        CallForegroundService.stop(appContext)
        scope.launch { resumeStore.clear() }
    }

    suspend fun pendingResume(): ResumeRecord? = resumeStore.load()

    fun dispose() {
        engine.dispose()
    }
}
