package io.sauti.android

import io.sauti.engine.IceCandidate
import io.sauti.engine.IceServer
import io.sauti.engine.PeerConnectionPort
import io.sauti.engine.RtcFactory
import io.sauti.engine.SdpDescription
import io.sauti.engine.StatsSample
import kotlinx.coroutines.CompletableDeferred

class RecordingRtcFactory(private val delegate: RtcFactory) : RtcFactory {
    val remoteAudio = CompletableDeferred<Unit>()
    val connections = mutableListOf<RecordingConnection>()

    override fun createPeerConnection(iceServers: List<IceServer>): PeerConnectionPort {
        val connection = RecordingConnection(delegate.createPeerConnection(iceServers)) {
            if (!remoteAudio.isCompleted) remoteAudio.complete(Unit)
        }
        connections.add(connection)
        return connection
    }
}

class RecordingConnection(
    private val delegate: PeerConnectionPort,
    private val onRemote: () -> Unit
) : PeerConnectionPort {

    @Volatile
    var lastLocalDescriptionType: String? = null
        private set

    override var onIceCandidate: ((IceCandidate) -> Unit)? = null
    override var onIceConnectionStateChange: ((String) -> Unit)? = null
    override var onNegotiationNeeded: (() -> Unit)? = null
    override var onRemoteAudio: (() -> Unit)? = null

    init {
        delegate.onIceCandidate = { candidate -> onIceCandidate?.invoke(candidate) }
        delegate.onIceConnectionStateChange = { state -> onIceConnectionStateChange?.invoke(state) }
        delegate.onNegotiationNeeded = { onNegotiationNeeded?.invoke() }
        delegate.onRemoteAudio = {
            onRemote()
            onRemoteAudio?.invoke()
        }
    }

    override val signalingState: String get() = delegate.signalingState
    override val iceConnectionState: String get() = delegate.iceConnectionState

    override suspend fun createOffer(iceRestart: Boolean): SdpDescription =
        delegate.createOffer(iceRestart)

    override suspend fun createAnswer(): SdpDescription = delegate.createAnswer()

    override suspend fun setLocalDescription(description: SdpDescription) {
        if (description.type != "rollback") lastLocalDescriptionType = description.type
        delegate.setLocalDescription(description)
    }

    override suspend fun setRemoteDescription(description: SdpDescription) =
        delegate.setRemoteDescription(description)

    override suspend fun rollbackLocalDescription() = delegate.rollbackLocalDescription()

    override suspend fun addIceCandidate(candidate: IceCandidate) =
        delegate.addIceCandidate(candidate)

    override fun restartIce() = delegate.restartIce()

    override suspend fun getStats(): StatsSample = delegate.getStats()

    override fun close() = delegate.close()
}
