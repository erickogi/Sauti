package io.sauti.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonElement

data class ParticipantSeed(
    val participantId: String,
    val metadata: Map<String, JsonElement>,
    val muted: Boolean,
    val onHold: Boolean,
    val connectionState: ConnectionState
)

class CallStore(now: () -> Long) {
    private val lock = Any()
    private val clock = DurationClock(now)
    private val participants = LinkedHashMap<String, ParticipantSnapshot>()

    private var selfId: String? = null
    private var phase: CallPhase = CallPhase.IDLE
    private var localMuted = false
    private var localOnHold = false
    private var socketReconnecting = false

    private val flow = MutableStateFlow(build())
    val state: StateFlow<CallState> get() = flow.asStateFlow()

    fun captureClockOffset(serverNow: Long) = synchronized(lock) { clock.captureOffset(serverNow) }

    fun setStartedAt(value: Long?) = synchronized(lock) {
        clock.setStartedAt(value)
        commit()
    }

    fun startedAt(): Long? = synchronized(lock) { clock.startedAt() }

    fun setSelf(id: String) = synchronized(lock) {
        selfId = id
    }

    fun selfId(): String? = synchronized(lock) { selfId }

    fun setPhase(value: CallPhase) = synchronized(lock) {
        phase = value
        commit()
    }

    fun upsertParticipant(seed: ParticipantSeed) = synchronized(lock) {
        val existing = participants[seed.participantId]
        participants[seed.participantId] = ParticipantSnapshot(
            participantId = seed.participantId,
            metadata = seed.metadata,
            muted = seed.muted,
            onHold = seed.onHold,
            connectionState = seed.connectionState,
            quality = existing?.quality ?: Quality.GOOD
        )
        commit()
    }

    fun removeParticipant(id: String) = synchronized(lock) {
        if (participants.remove(id) != null) commit()
    }

    fun mergeState(id: String, partial: PartialState) = synchronized(lock) {
        val current = participants[id] ?: return@synchronized
        participants[id] = current.copy(
            muted = partial.muted ?: current.muted,
            onHold = partial.onHold ?: current.onHold
        )
        commit()
    }

    fun setConnectionState(id: String, connectionState: ConnectionState) = synchronized(lock) {
        val current = participants[id] ?: return@synchronized
        participants[id] = current.copy(connectionState = connectionState)
        commit()
    }

    fun setQuality(id: String, quality: Quality) = synchronized(lock) {
        val current = participants[id] ?: return@synchronized
        if (current.quality == quality) return@synchronized
        participants[id] = current.copy(quality = quality)
        commit()
    }

    fun participant(id: String): ParticipantSnapshot? = synchronized(lock) { participants[id] }

    fun setLocalMuted(muted: Boolean) = synchronized(lock) {
        localMuted = muted
        val self = selfId
        if (self != null) mergeState(self, PartialState(muted = muted)) else commit()
    }

    fun setLocalOnHold(onHold: Boolean) = synchronized(lock) {
        localOnHold = onHold
        val self = selfId
        if (self != null) mergeState(self, PartialState(onHold = onHold)) else commit()
    }

    fun setSocketReconnecting(value: Boolean) = synchronized(lock) {
        socketReconnecting = value
        commit()
    }

    fun tick() = synchronized(lock) { commit() }

    fun reset() = synchronized(lock) {
        participants.clear()
        selfId = null
        phase = CallPhase.LEFT
        localMuted = false
        localOnHold = false
        socketReconnecting = false
        clock.setStartedAt(null)
        commit()
    }

    private fun build(): CallState {
        val snapshots = participants.values.toList()
        var aggregate = Quality.GOOD
        var reconnecting = socketReconnecting
        for (p in snapshots) {
            if (p.participantId != selfId) {
                aggregate = worstQuality(aggregate, p.quality)
            }
            if (p.connectionState == ConnectionState.RECONNECTING || p.connectionState == ConnectionState.UNREACHABLE) {
                reconnecting = true
            }
        }
        return CallState(
            phase = phase,
            participants = snapshots,
            localMuted = localMuted,
            localOnHold = localOnHold,
            durationMs = clock.elapsedMs(),
            quality = aggregate,
            reconnecting = reconnecting,
            selfId = selfId
        )
    }

    private fun commit() {
        flow.value = build()
    }
}
