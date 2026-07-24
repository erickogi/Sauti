package io.sauti.android.rtc

import android.content.Context
import io.sauti.engine.IceServer
import io.sauti.engine.LocalMediaController
import io.sauti.engine.PeerConnectionPort
import io.sauti.engine.RtcFactory
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.audio.JavaAudioDeviceModule
import org.webrtc.PeerConnection.IceServer as WebRtcIceServer

class WebRtcFactory(context: Context) : RtcFactory, LocalMediaController {

    private val appContext = context.applicationContext
    private val streamId = "sauti-stream"

    private val factory: PeerConnectionFactory
    private val audioSource: AudioSource
    private val localAudioTrack: AudioTrack

    init {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(appContext)
                .createInitializationOptions()
        )
        val audioModule = JavaAudioDeviceModule.builder(appContext)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()
        factory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(audioModule)
            .createPeerConnectionFactory()
        audioSource = factory.createAudioSource(MediaConstraints())
        localAudioTrack = factory.createAudioTrack("sauti-audio", audioSource)
        localAudioTrack.setEnabled(true)
    }

    override fun createPeerConnection(iceServers: List<IceServer>): PeerConnectionPort {
        val servers = iceServers.map { server ->
            WebRtcIceServer.builder(server.urls)
                .apply {
                    if (server.username != null) setUsername(server.username)
                    if (server.credential != null) setPassword(server.credential)
                }
                .createIceServer()
        }
        val config = PeerConnection.RTCConfiguration(servers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        val port = WebRtcConnection(streamId)
        val connection = factory.createPeerConnection(config, port)
            ?: throw RtcException("factory returned a null peer connection")
        port.connection = connection
        connection.addTrack(localAudioTrack, listOf(streamId))
        return port
    }

    override fun setAudioEnabled(enabled: Boolean) {
        localAudioTrack.setEnabled(enabled)
    }

    fun dispose() {
        localAudioTrack.dispose()
        audioSource.dispose()
        factory.dispose()
    }
}
