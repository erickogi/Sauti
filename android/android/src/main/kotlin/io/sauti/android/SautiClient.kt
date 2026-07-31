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
import io.sauti.android.proximity.ProximityController
import io.sauti.android.service.CallForegroundService
import io.sauti.android.service.CallPresence
import io.sauti.android.telephony.InterruptionPolicy
import io.sauti.android.telephony.InterruptionReducer
import io.sauti.android.telephony.TelephonyWatcher
import io.sauti.android.telephony.UserAudioIntent
import io.sauti.engine.AudioProcessingConfig
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
    val displayTitle: String,
    val endWhenLastPeerLeaves: Boolean = false
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
    private val interruptionPolicy: InterruptionPolicy = InterruptionPolicy(),
    enableProximity: Boolean = false,
    proximityFactory: (Context, CoroutineScope, StateFlow<CallState>, StateFlow<AudioDevice>) -> ProximityController =
        { ctx, sc, state, device -> ProximityController(ctx, sc, state, device) }
) : SautiCall {
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

    private val proximity: ProximityController? =
        if (enableProximity) {
            proximityFactory(appContext, scope, engine.state, audio.currentDevice)
        } else {
            null
        }

    private var userMuted = false
    private var userHeld = false

    override val state: StateFlow<CallState> get() = engine.state
    override val events: SharedFlow<CallEvent> get() = engine.events
    override val currentDevice: StateFlow<AudioDevice> get() = audio.currentDevice
    override val availableDevices: StateFlow<Set<AudioDevice>> get() = audio.availableDevices
    override val interrupted: StateFlow<Boolean> get() = audio.interrupted
    override var selfParticipantId: String? = null
        private set

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
        interruptionPolicy: InterruptionPolicy = InterruptionPolicy(),
        enableProximity: Boolean = false,
        audioProcessing: AudioProcessingConfig = AudioProcessingConfig()
    ) : this(
        context = context,
        scope = scope,
        audio = AudioSessionCoordinator(context.applicationContext),
        engine = WebRtcCallEngine(context.applicationContext, engineConfig, scope, audioProcessing),
        resumeStore = ResumeStore(context.applicationContext),
        telephonyFactory = { ctx, cb -> TelephonyWatcher(ctx, cb) },
        connectivityFactory = { ctx, cb -> ConnectivityWatcher(ctx, cb) },
        connectivityPolicy = connectivityPolicy,
        interruptionPolicy = interruptionPolicy,
        enableProximity = enableProximity
    )

    suspend fun join(request: SautiJoinRequest) {
        selfParticipantId = request.participantId
        CallForegroundService.start(appContext, request.displayTitle, CallPresence.CONNECTING)
        audio.start()
        telephony.start()
        connectivity.start()
        proximity?.start()
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
        engine.join(JoinConfig(request.url, request.token, request.endWhenLastPeerLeaves))
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

    override fun setMuted(muted: Boolean) {
        userMuted = muted
        engine.setMuted(muted)
    }

    override fun setHold(onHold: Boolean) {
        userHeld = onHold
        engine.setHold(onHold)
    }

    override fun selectDevice(device: AudioDevice) = audio.selectDevice(device)

    override fun hangUp() = leave()

    private var left = false
    private var disposed = false

    fun leave() {
        if (left) return
        leaveInternal()
        CallForegroundService.stop(appContext)
    }

    internal fun leaveInternal() {
        if (left) return
        left = true
        engine.leave()
        proximity?.stop()
        telephony.stop()
        connectivity.stop()
        audio.stop()
        scope.launch { resumeStore.clear() }
    }

    suspend fun pendingResume(): ResumeRecord? = resumeStore.load()

    fun dispose() {
        if (disposed) return
        disposed = true
        engine.dispose()
    }
}
