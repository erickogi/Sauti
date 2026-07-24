package io.sauti.engine

interface PeerConnectionPort {
    var onIceCandidate: ((IceCandidate) -> Unit)?
    var onIceConnectionStateChange: ((String) -> Unit)?
    var onNegotiationNeeded: (() -> Unit)?
    var onRemoteAudio: (() -> Unit)?

    val signalingState: String
    val iceConnectionState: String

    suspend fun createOffer(iceRestart: Boolean = false): SdpDescription
    suspend fun createAnswer(): SdpDescription
    suspend fun setLocalDescription(description: SdpDescription)
    suspend fun setRemoteDescription(description: SdpDescription)
    suspend fun rollbackLocalDescription()
    suspend fun addIceCandidate(candidate: IceCandidate)
    fun restartIce()
    suspend fun getStats(): StatsSample
    fun close()
}

interface RtcFactory {
    fun createPeerConnection(iceServers: List<IceServer>): PeerConnectionPort
}

interface LocalMediaController {
    fun setAudioEnabled(enabled: Boolean)
}

interface SignalingTransport {
    fun send(text: String)
    fun close()
}

interface TransportCallbacks {
    fun onOpen()
    fun onMessage(text: String)
    fun onClosed(code: Int)
    fun onFailure(error: Throwable)
}

interface SignalingTransportFactory {
    fun connect(url: String, callbacks: TransportCallbacks): SignalingTransport
}
