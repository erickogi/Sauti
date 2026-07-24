package io.sauti.rx2

import io.sauti.engine.CallState
import io.sauti.engine.IceCandidate
import io.sauti.engine.IceServer
import io.sauti.engine.PeerConnectionPort
import io.sauti.engine.RtcFactory
import io.sauti.engine.SdpDescription
import io.sauti.engine.SignalingTransport
import io.sauti.engine.SignalingTransportFactory
import io.sauti.engine.StatsSample
import io.sauti.engine.TransportCallbacks
import io.sauti.engine.CallSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

private class NoopTransport : SignalingTransport {
    override fun send(text: String) = Unit
    override fun close() = Unit
}

private class NoopTransportFactory : SignalingTransportFactory {
    override fun connect(url: String, callbacks: TransportCallbacks): SignalingTransport = NoopTransport()
}

private class NoopPeer : PeerConnectionPort {
    override var onIceCandidate: ((IceCandidate) -> Unit)? = null
    override var onIceConnectionStateChange: ((String) -> Unit)? = null
    override var onNegotiationNeeded: (() -> Unit)? = null
    override var onRemoteAudio: (() -> Unit)? = null
    override val signalingState: String = "stable"
    override val iceConnectionState: String = "new"
    override suspend fun createOffer(iceRestart: Boolean): SdpDescription = SdpDescription("offer", "s")
    override suspend fun createAnswer(): SdpDescription = SdpDescription("answer", "s")
    override suspend fun setLocalDescription(description: SdpDescription) = Unit
    override suspend fun setRemoteDescription(description: SdpDescription) = Unit
    override suspend fun rollbackLocalDescription() = Unit
    override suspend fun addIceCandidate(candidate: IceCandidate) = Unit
    override fun restartIce() = Unit
    override suspend fun getStats(): StatsSample = StatsSample(0.0, 0.0, 0.0)
    override fun close() = Unit
}

private class NoopRtcFactory : RtcFactory {
    override fun createPeerConnection(iceServers: List<IceServer>): PeerConnectionPort = NoopPeer()
}

class RxCallSessionTest {
    @Test
    fun stateObservableEmitsInitialSnapshot() = runTest {
        val session = CallSession(
            rtcFactory = NoopRtcFactory(),
            transportFactory = NoopTransportFactory(),
            scope = backgroundScope
        )
        val rx = RxCallSession(session, Dispatchers.Unconfined)
        val observed = rx.state().blockingFirst()
        assertTrue(observed is CallState)
    }

    @Test
    fun commandsReturnCompletables() = runTest {
        val session = CallSession(
            rtcFactory = NoopRtcFactory(),
            transportFactory = NoopTransportFactory(),
            scope = backgroundScope
        )
        val rx = RxCallSession(session, Dispatchers.Unconfined)
        rx.setMuted(true).blockingAwait()
        rx.setHold(true).blockingAwait()
        assertTrue(true)
    }
}
