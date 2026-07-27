package io.sauti.engine

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

data class JoinConfig(
    val url: String,
    val token: String,
    val endWhenLastPeerLeaves: Boolean = false
)

data class EngineConfig(
    val graceMs: Long = 30_000,
    val maxReconnectAttempts: Int = 8,
    val reconnectBaseMs: Long = 500,
    val reconnectMaxMs: Long = 30_000,
    val statsIntervalMs: Long = 2_000,
    val now: () -> Long = { System.currentTimeMillis() },
    val random: () -> Double = { Math.random() }
)

sealed interface CallEvent {
    data class Joined(val participantId: String) : CallEvent
    data class Left(val participantId: String) : CallEvent
    data class Unreachable(val participantId: String) : CallEvent
    object Reconnecting : CallEvent
    object Reconnected : CallEvent
    data class StateChanged(val participantId: String, val muted: Boolean, val onHold: Boolean) : CallEvent
    data class QualityChanged(val participantId: String, val quality: Quality) : CallEvent
    data class Failure(val error: SautiException) : CallEvent
}

class CallSession(
    private val rtcFactory: RtcFactory,
    private val transportFactory: SignalingTransportFactory,
    private val localMedia: LocalMediaController? = null,
    private val config: EngineConfig = EngineConfig(),
    private val scope: CoroutineScope = MainScope()
) {
    private val store = CallStore(config.now)
    val state: StateFlow<CallState> get() = store.state

    private val events = MutableSharedFlow<CallEvent>(extraBufferCapacity = 64)
    val eventFlow: SharedFlow<CallEvent> get() = events.asSharedFlow()

    private val trackers = LinkedHashMap<String, QualityTracker>()

    private var signaling: SignalingClient? = null
    private var mesh: Mesh? = null
    private var maxParticipants: Int = 3
    private var active = false
    private var localMuted = false
    private var localOnHold = false
    private var endWhenLastPeerLeaves = false
    private var hadRemotePeer = false

    private var durationJob: Job? = null
    private var qualityJob: Job? = null
    private var joinDeferred: CompletableDeferred<Unit>? = null

    suspend fun join(config: JoinConfig) {
        if (active) return
        active = true
        endWhenLastPeerLeaves = config.endWhenLastPeerLeaves
        store.setPhase(CallPhase.CONNECTING)
        applyLocalAudio()

        val deferred = CompletableDeferred<Unit>()
        joinDeferred = deferred

        signaling = SignalingClient(
            scope,
            transportFactory,
            SignalingConfig(
                maxReconnectAttempts = this.config.maxReconnectAttempts,
                reconnectBaseMs = this.config.reconnectBaseMs,
                reconnectMaxMs = this.config.reconnectMaxMs,
                random = this.config.random
            ),
            object : SignalingHooks {
                override fun onFrame(frame: ServerFrame) = handleFrame(frame)
                override fun onVersionMismatch(version: Int?) = surface(VersionMismatchException(version))
                override fun onReconnecting() = enterReconnecting()
                override fun onTerminal(reason: String) = failJoin(DisconnectedException(reason))
            }
        )
        signaling?.connect(config.url, config.token)
        deferred.await()
    }

    fun setMuted(muted: Boolean) {
        localMuted = muted
        applyLocalAudio()
        store.setLocalMuted(muted)
        signaling?.send(FrameCodec.encodeState(muted = muted, onHold = null))
    }

    fun setHold(onHold: Boolean) {
        localOnHold = onHold
        applyLocalAudio()
        store.setLocalOnHold(onHold)
        signaling?.send(FrameCodec.encodeState(muted = null, onHold = onHold))
    }

    fun onNetworkChanged() {
        mesh?.iceRestartAll()
    }

    fun leave() {
        if (!active) return
        signaling?.send(FrameCodec.encodeLeave())
        signaling?.close()
        teardown()
    }

    private fun applyLocalAudio() {
        localMedia?.setAudioEnabled(!(localMuted || localOnHold))
    }

    private fun remoteCount(): Int =
        store.state.value.participants.count { it.participantId != store.selfId() }

    private fun maybeEndForLastPeer() {
        if (store.state.value.phase != CallPhase.CONNECTED) return
        if (remoteCount() > 0) {
            hadRemotePeer = true
            return
        }
        if (endWhenLastPeerLeaves && hadRemotePeer) {
            hadRemotePeer = false
            leave()
        }
    }

    private fun handleFrame(frame: ServerFrame) {
        when (frame) {
            is ServerFrame.Ready -> onReady(frame)
            is ServerFrame.ParticipantJoined -> onParticipantJoined(frame.participant)
            is ServerFrame.ParticipantStateChanged -> {
                store.mergeState(frame.participantId, frame.state)
                emitStateChanged(frame.participantId)
            }
            is ServerFrame.ParticipantUnreachable -> {
                store.setConnectionState(frame.participantId, ConnectionState.RECONNECTING)
                emit(CallEvent.Unreachable(frame.participantId))
            }
            is ServerFrame.ParticipantLeft -> {
                mesh?.removePeer(frame.participantId)
                store.removeParticipant(frame.participantId)
                trackers.remove(frame.participantId)
                emit(CallEvent.Left(frame.participantId))
                maybeEndForLastPeer()
            }
            is ServerFrame.RoomStarted -> {
                store.setStartedAt(frame.startedAt)
                startDuration()
            }
            is ServerFrame.ErrorFrame -> onServerError(frame.code, frame.message)
            is ServerFrame.Offer -> scope.launch { routeSafely { mesh?.routeOffer(frame.from, frame.sdp) } }
            is ServerFrame.Answer -> scope.launch { routeSafely { mesh?.routeAnswer(frame.from, frame.sdp) } }
            is ServerFrame.Ice -> scope.launch { routeSafely { mesh?.routeIce(frame.from, frame.candidate) } }
        }
    }

    private suspend fun routeSafely(block: suspend () -> Unit) {
        try {
            block()
        } catch (error: Throwable) {
            surface(DisconnectedException(error.message ?: "media routing failed"))
        }
    }

    private fun onReady(frame: ServerFrame.Ready) {
        store.captureClockOffset(frame.serverNow)
        maxParticipants = frame.room.maxParticipants

        if (!frame.resumed && frame.peers.size >= frame.room.maxParticipants) {
            failJoin(RoomFullException("the room is already at capacity"))
            return
        }

        val selfId = frame.self.participantId
        store.setSelf(selfId)

        val currentMesh = mesh ?: Mesh(scope, rtcFactory, selfId, object : MeshHooks {
            override fun send(text: String) {
                signaling?.send(text)
            }

            override fun onConnected(peerId: String) {
                store.setConnectionState(peerId, ConnectionState.CONNECTED)
            }

            override fun onRemoteAudio(peerId: String) = Unit

            override fun onError(error: Throwable) {
                surface(DisconnectedException(error.message ?: "peer error"))
            }
        }).also { mesh = it }

        currentMesh.setIceServers(frame.iceServers)

        store.upsertParticipant(
            ParticipantSeed(
                participantId = selfId,
                metadata = frame.self.metadata,
                muted = localMuted,
                onHold = localOnHold,
                connectionState = ConnectionState.CONNECTED
            )
        )

        if (frame.resumed) {
            reconcile(frame, currentMesh)
        } else {
            for (id in currentMesh.ids()) {
                currentMesh.removePeer(id)
                store.removeParticipant(id)
                trackers.remove(id)
            }
            for (peer in frame.peers) {
                seedPeer(peer, ConnectionState.CONNECTING)
                currentMesh.addPeer(peer.participantId)
            }
        }

        store.setStartedAt(frame.room.startedAt)
        if (frame.room.startedAt != null) startDuration()
        startQuality()

        store.setSocketReconnecting(false)
        store.setPhase(CallPhase.CONNECTED)
        if (frame.resumed) emit(CallEvent.Reconnected)
        finishJoin()
        maybeEndForLastPeer()
    }

    private fun reconcile(frame: ServerFrame.Ready, currentMesh: Mesh) {
        val present = frame.peers.map { it.participantId }.toSet()
        for (id in currentMesh.ids()) {
            if (id !in present) {
                currentMesh.removePeer(id)
                store.removeParticipant(id)
                trackers.remove(id)
            }
        }
        for (peer in frame.peers) {
            val existing = currentMesh.has(peer.participantId)
            seedPeer(peer, if (existing) ConnectionState.CONNECTED else ConnectionState.CONNECTING)
            if (existing) {
                currentMesh.statsPeer(peer.participantId)?.iceRestart()
            } else {
                currentMesh.addPeer(peer.participantId)
            }
        }
    }

    private fun seedPeer(peer: Participant, connectionState: ConnectionState) {
        store.upsertParticipant(
            ParticipantSeed(
                participantId = peer.participantId,
                metadata = peer.metadata,
                muted = peer.state.muted,
                onHold = peer.state.onHold,
                connectionState = connectionState
            )
        )
    }

    private fun onParticipantJoined(participant: Participant) {
        val currentMesh = mesh ?: return
        if (currentMesh.count() >= maxParticipants - 1) return
        seedPeer(participant, ConnectionState.CONNECTING)
        currentMesh.addPeer(participant.participantId)
        emit(CallEvent.Joined(participant.participantId))
        maybeEndForLastPeer()
    }

    private fun onServerError(code: String, message: String) {
        surface(ServerErrorException(code, message))
        if (isFatalServerCode(code)) failJoin(ServerErrorException(code, message))
    }

    private fun enterReconnecting() {
        store.setSocketReconnecting(true)
        mesh?.let { m ->
            for (id in m.ids()) store.setConnectionState(id, ConnectionState.RECONNECTING)
        }
        emit(CallEvent.Reconnecting)
    }

    private fun startDuration() {
        if (durationJob != null) return
        durationJob = scope.launch {
            while (true) {
                delay(1_000)
                store.tick()
            }
        }
    }

    private fun startQuality() {
        if (qualityJob != null) return
        qualityJob = scope.launch {
            while (true) {
                delay(config.statsIntervalMs)
                pollQuality()
            }
        }
    }

    suspend fun pollQuality() {
        val currentMesh = mesh ?: return
        for (peerId in currentMesh.ids()) {
            val peer = currentMesh.statsPeer(peerId) ?: continue
            val sample = try {
                peer.pc.getStats()
            } catch (error: Throwable) {
                continue
            }
            val tracker = trackers.getOrPut(peerId) { QualityTracker() }
            val result = tracker.observe(sample)
            val before = store.participant(peerId)?.quality
            store.setQuality(peerId, result.label)
            if (before != null && before != result.label) {
                emit(CallEvent.QualityChanged(peerId, result.label))
            }
        }
    }

    private fun emitStateChanged(id: String) {
        val snapshot = store.participant(id) ?: return
        emit(CallEvent.StateChanged(id, snapshot.muted, snapshot.onHold))
    }

    private fun surface(error: SautiException) {
        emit(CallEvent.Failure(error))
    }

    private fun emit(event: CallEvent) {
        events.tryEmit(event)
    }

    private fun failJoin(error: SautiException) {
        surface(error)
        signaling?.close()
        teardown()
        joinDeferred?.completeExceptionally(error)
        joinDeferred = null
    }

    private fun finishJoin() {
        joinDeferred?.complete(Unit)
        joinDeferred = null
    }

    private fun teardown() {
        mesh?.teardown()
        mesh = null
        durationJob?.cancel()
        durationJob = null
        qualityJob?.cancel()
        qualityJob = null
        trackers.clear()
        signaling = null
        active = false
        localMuted = false
        localOnHold = false
        endWhenLastPeerLeaves = false
        hadRemotePeer = false
        store.reset()
    }
}
