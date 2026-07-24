package io.sauti.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

interface PeerSignal {
    fun send(text: String)
    fun onRemoteAudio(peerId: String)
    fun onUnrecoverable(peerId: String)
    fun onConnected(peerId: String)
    fun onError(error: Throwable)
}

class Peer(
    val peerId: String,
    selfId: String,
    val pc: PeerConnectionPort,
    private val scope: CoroutineScope,
    private val signal: PeerSignal
) {
    val polite: Boolean = Politeness.isPolite(selfId, peerId)

    private var makingOffer = false
    private var ignoreOffer = false
    private var restartAttempted = false
    private var restartRequested = false

    init {
        pc.onNegotiationNeeded = {
            scope.launch { makeOffer() }
        }
        pc.onIceCandidate = { candidate ->
            signal.send(FrameCodec.encodeIce(peerId, candidate))
        }
        pc.onRemoteAudio = {
            signal.onRemoteAudio(peerId)
        }
        pc.onIceConnectionStateChange = { state ->
            if (state == "connected" || state == "completed") {
                restartAttempted = false
                signal.onConnected(peerId)
            } else if (state == "failed") {
                recover()
            }
        }
    }

    fun start() {
        if (!polite) scope.launch { makeOffer() }
    }

    private suspend fun makeOffer() {
        if (makingOffer) return
        makingOffer = true
        val iceRestart = restartRequested
        restartRequested = false
        try {
            val offer = pc.createOffer(iceRestart = iceRestart)
            pc.setLocalDescription(offer)
            signal.send(FrameCodec.encodeOffer(peerId, offer.sdp))
        } catch (error: Throwable) {
            signal.onError(error)
        } finally {
            makingOffer = false
        }
    }

    suspend fun onOffer(sdp: String) {
        val collision = makingOffer || pc.signalingState != "stable"
        ignoreOffer = !polite && collision
        if (ignoreOffer) return
        if (collision) {
            pc.rollbackLocalDescription()
        }
        pc.setRemoteDescription(SdpDescription("offer", sdp))
        val answer = pc.createAnswer()
        pc.setLocalDescription(answer)
        signal.send(FrameCodec.encodeAnswer(peerId, answer.sdp))
    }

    suspend fun onAnswer(sdp: String) {
        pc.setRemoteDescription(SdpDescription("answer", sdp))
    }

    suspend fun onIce(candidate: IceCandidate) {
        try {
            pc.addIceCandidate(candidate)
        } catch (error: Throwable) {
            if (!ignoreOffer) signal.onError(error)
        }
    }

    fun iceRestart() {
        restartRequested = true
        pc.restartIce()
    }

    private fun recover() {
        if (!restartAttempted) {
            restartAttempted = true
            restartRequested = true
            pc.restartIce()
        } else {
            signal.onUnrecoverable(peerId)
        }
    }

    fun close() {
        pc.onIceCandidate = null
        pc.onRemoteAudio = null
        pc.onNegotiationNeeded = null
        pc.onIceConnectionStateChange = null
        pc.close()
    }
}
