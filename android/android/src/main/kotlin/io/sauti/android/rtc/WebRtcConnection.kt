package io.sauti.android.rtc

import io.sauti.engine.IceCandidate
import io.sauti.engine.PeerConnectionPort
import io.sauti.engine.SdpDescription
import io.sauti.engine.StatsSample
import kotlinx.coroutines.suspendCancellableCoroutine
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import org.webrtc.IceCandidate as WebRtcIceCandidate

class WebRtcConnection(
    private val streamId: String
) : PeerConnectionPort, PeerConnection.Observer {

    lateinit var connection: PeerConnection

    override var onIceCandidate: ((IceCandidate) -> Unit)? = null
    override var onIceConnectionStateChange: ((String) -> Unit)? = null
    override var onNegotiationNeeded: (() -> Unit)? = null
    override var onRemoteAudio: (() -> Unit)? = null

    override val signalingState: String
        get() = when (connection.signalingState()) {
            PeerConnection.SignalingState.STABLE -> "stable"
            PeerConnection.SignalingState.HAVE_LOCAL_OFFER -> "have-local-offer"
            PeerConnection.SignalingState.HAVE_REMOTE_OFFER -> "have-remote-offer"
            PeerConnection.SignalingState.HAVE_LOCAL_PRANSWER -> "have-local-pranswer"
            PeerConnection.SignalingState.HAVE_REMOTE_PRANSWER -> "have-remote-pranswer"
            PeerConnection.SignalingState.CLOSED -> "closed"
            else -> "unknown"
        }

    override val iceConnectionState: String
        get() = when (connection.iceConnectionState()) {
            PeerConnection.IceConnectionState.NEW -> "new"
            PeerConnection.IceConnectionState.CHECKING -> "checking"
            PeerConnection.IceConnectionState.CONNECTED -> "connected"
            PeerConnection.IceConnectionState.COMPLETED -> "completed"
            PeerConnection.IceConnectionState.FAILED -> "failed"
            PeerConnection.IceConnectionState.DISCONNECTED -> "disconnected"
            PeerConnection.IceConnectionState.CLOSED -> "closed"
            else -> "unknown"
        }

    override suspend fun createOffer(iceRestart: Boolean): SdpDescription {
        val constraints = MediaConstraints().apply {
            if (iceRestart) {
                mandatory.add(MediaConstraints.KeyValuePair("IceRestart", "true"))
            }
        }
        val description = suspendCancellableCoroutine { cont ->
            connection.createOffer(object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription) = cont.resume(sdp)
                override fun onSetSuccess() = Unit
                override fun onCreateFailure(error: String?) = cont.resumeWithException(RtcException(error))
                override fun onSetFailure(error: String?) = Unit
            }, constraints)
        }
        return SdpDescription("offer", description.description)
    }

    override suspend fun createAnswer(): SdpDescription {
        val description = suspendCancellableCoroutine { cont ->
            connection.createAnswer(object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription) = cont.resume(sdp)
                override fun onSetSuccess() = Unit
                override fun onCreateFailure(error: String?) = cont.resumeWithException(RtcException(error))
                override fun onSetFailure(error: String?) = Unit
            }, MediaConstraints())
        }
        return SdpDescription("answer", description.description)
    }

    override suspend fun setLocalDescription(description: SdpDescription) {
        applyLocal(SessionDescription(typeOf(description.type), description.sdp))
    }

    override suspend fun setRemoteDescription(description: SdpDescription) {
        val session = SessionDescription(typeOf(description.type), description.sdp)
        suspendCancellableCoroutine { cont ->
            connection.setRemoteDescription(object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription?) = Unit
                override fun onSetSuccess() = cont.resume(Unit)
                override fun onCreateFailure(error: String?) = Unit
                override fun onSetFailure(error: String?) = cont.resumeWithException(RtcException(error))
            }, session)
        }
    }

    override suspend fun rollbackLocalDescription() {
        applyLocal(SessionDescription(SessionDescription.Type.ROLLBACK, ""))
    }

    private suspend fun applyLocal(session: SessionDescription) {
        suspendCancellableCoroutine { cont ->
            connection.setLocalDescription(object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription?) = Unit
                override fun onSetSuccess() = cont.resume(Unit)
                override fun onCreateFailure(error: String?) = Unit
                override fun onSetFailure(error: String?) = cont.resumeWithException(RtcException(error))
            }, session)
        }
    }

    override suspend fun addIceCandidate(candidate: IceCandidate) {
        connection.addIceCandidate(
            WebRtcIceCandidate(candidate.sdpMid ?: "", candidate.sdpMLineIndex ?: 0, candidate.candidate)
        )
    }

    override fun restartIce() {
        connection.restartIce()
    }

    override suspend fun getStats(): StatsSample = suspendCancellableCoroutine { cont ->
        connection.getStats { report ->
            cont.resume(StatsExtractor.extract(report))
        }
    }

    override fun close() {
        connection.close()
        connection.dispose()
    }

    override fun onIceCandidate(candidate: WebRtcIceCandidate) {
        onIceCandidate?.invoke(
            IceCandidate(candidate.sdp, candidate.sdpMid, candidate.sdpMLineIndex)
        )
    }

    override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
        onIceConnectionStateChange?.invoke(iceConnectionState)
    }

    override fun onRenegotiationNeeded() {
        onNegotiationNeeded?.invoke()
    }

    override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
        onRemoteAudio?.invoke()
    }

    override fun onSignalingChange(state: PeerConnection.SignalingState?) = Unit
    override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
    override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) = Unit
    override fun onIceCandidatesRemoved(candidates: Array<out WebRtcIceCandidate>?) = Unit
    override fun onAddStream(stream: MediaStream?) = Unit
    override fun onRemoveStream(stream: MediaStream?) = Unit
    override fun onDataChannel(channel: org.webrtc.DataChannel?) = Unit

    private fun typeOf(type: String): SessionDescription.Type = when (type) {
        "offer" -> SessionDescription.Type.OFFER
        "answer" -> SessionDescription.Type.ANSWER
        "rollback" -> SessionDescription.Type.ROLLBACK
        else -> SessionDescription.Type.OFFER
    }
}

class RtcException(message: String?) : Exception(message ?: "webrtc operation failed")
