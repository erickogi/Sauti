package io.sauti.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class FakeTransport(
    private val url: String,
    private val callbacks: TransportCallbacks,
    private val hub: FakeTransportHub
) : SignalingTransport {
    val sent = mutableListOf<String>()
    var closed = false

    override fun send(text: String) {
        sent += text
    }

    override fun close() {
        closed = true
    }

    fun open() {
        callbacks.onOpen()
    }

    fun deliver(text: String) {
        callbacks.onMessage(text)
    }

    fun dropByServer(code: Int = 1006) {
        callbacks.onClosed(code)
    }
}

class FakeTransportHub : SignalingTransportFactory {
    val transports = mutableListOf<FakeTransport>()
    var autoOpen = true

    val latest: FakeTransport get() = transports.last()

    override fun connect(url: String, callbacks: TransportCallbacks): SignalingTransport {
        val transport = FakeTransport(url, callbacks, this)
        transports += transport
        if (autoOpen) transport.open()
        return transport
    }

    fun sentTypes(): List<String> = transports.flatMap { it.sent }.map { typeOf(it) }

    fun joinTokens(): List<String> = transports.flatMap { t ->
        t.sent.filter { typeOf(it) == "join" }.map { Json.parseToJsonElement(it).jsonObject.getValue("token").jsonPrimitive.content }
    }

    companion object {
        fun typeOf(frame: String): String =
            Json.parseToJsonElement(frame).jsonObject.getValue("type").jsonPrimitive.content
    }
}

class FakePeerConnection : PeerConnectionPort {
    override var onIceCandidate: ((IceCandidate) -> Unit)? = null
    override var onIceConnectionStateChange: ((String) -> Unit)? = null
    override var onNegotiationNeeded: (() -> Unit)? = null
    override var onRemoteAudio: (() -> Unit)? = null

    override var signalingState: String = "stable"
    override var iceConnectionState: String = "new"

    var createdOffers = 0
    var iceRestartOffers = 0
    var restartIceCalls = 0
    var closed = false
    var localDescription: SdpDescription? = null
    var remoteDescription: SdpDescription? = null
    val addedCandidates = mutableListOf<IceCandidate>()
    var stats = StatsSample(rttMs = 10.0, loss = 0.0, jitterMs = 5.0)
    var throwOnStats = false

    override suspend fun createOffer(iceRestart: Boolean): SdpDescription {
        createdOffers += 1
        if (iceRestart) iceRestartOffers += 1
        return SdpDescription("offer", "offer-sdp-$createdOffers")
    }

    override suspend fun createAnswer(): SdpDescription = SdpDescription("answer", "answer-sdp")

    override suspend fun setLocalDescription(description: SdpDescription) {
        localDescription = description
        signalingState = if (description.type == "offer") "have-local-offer" else "stable"
    }

    override suspend fun setRemoteDescription(description: SdpDescription) {
        remoteDescription = description
        signalingState = if (description.type == "offer") "have-remote-offer" else "stable"
    }

    override suspend fun rollbackLocalDescription() {
        localDescription = null
        signalingState = "stable"
    }

    override suspend fun addIceCandidate(candidate: IceCandidate) {
        addedCandidates += candidate
    }

    override fun restartIce() {
        restartIceCalls += 1
        onNegotiationNeeded?.invoke()
    }

    override suspend fun getStats(): StatsSample {
        if (throwOnStats) throw IllegalStateException("stats unavailable")
        return stats
    }

    override fun close() {
        closed = true
    }

    fun emitConnected() {
        iceConnectionState = "connected"
        onIceConnectionStateChange?.invoke(iceConnectionState)
    }

    fun emitFailed() {
        iceConnectionState = "failed"
        onIceConnectionStateChange?.invoke(iceConnectionState)
    }
}

class FakeRtcFactory : RtcFactory {
    val created = mutableListOf<FakePeerConnection>()

    override fun createPeerConnection(iceServers: List<IceServer>): PeerConnectionPort {
        val pc = FakePeerConnection()
        created += pc
        return pc
    }
}

class RecordingLocalMedia : LocalMediaController {
    val enabledHistory = mutableListOf<Boolean>()
    override fun setAudioEnabled(enabled: Boolean) {
        enabledHistory += enabled
    }
}

object Frames {
    fun participant(
        id: String,
        muted: Boolean = false,
        onHold: Boolean = false,
        connectionState: String = "connected"
    ): JsonObject = buildJsonObject {
        put("participantId", id)
        put("joinedAt", 1000L)
        put("metadata", buildJsonObject { })
        put("connectionState", connectionState)
        put("state", buildJsonObject {
            put("muted", muted)
            put("onHold", onHold)
        })
    }

    fun ready(
        selfId: String,
        peers: List<JsonObject> = emptyList(),
        startedAt: Long? = null,
        serverNow: Long = 5000L,
        maxParticipants: Int = 3,
        resumed: Boolean = false
    ): String = buildJsonObject {
        put("v", 1)
        put("type", "ready")
        put("self", participant(selfId))
        put("peers", buildJsonArray { peers.forEach { add(it) } })
        put("room", buildJsonObject {
            put("roomId", "room-1")
            if (startedAt != null) put("startedAt", startedAt) else put("startedAt", JsonNull)
            put("maxParticipants", maxParticipants)
        })
        put("iceServers", buildJsonArray { })
        put("serverNow", serverNow)
        put("resumed", resumed)
    }.toString()

    fun participantJoined(id: String): String = buildJsonObject {
        put("v", 1)
        put("type", "participant-joined")
        put("participant", participant(id, connectionState = "connecting"))
    }.toString()

    fun participantState(id: String, muted: Boolean? = null, onHold: Boolean? = null): String = buildJsonObject {
        put("v", 1)
        put("type", "participant-state")
        put("participantId", id)
        put("state", buildJsonObject {
            if (muted != null) put("muted", muted)
            if (onHold != null) put("onHold", onHold)
        })
    }.toString()

    fun participantUnreachable(id: String): String = buildJsonObject {
        put("v", 1)
        put("type", "participant-unreachable")
        put("participantId", id)
    }.toString()

    fun participantLeft(id: String): String = buildJsonObject {
        put("v", 1)
        put("type", "participant-left")
        put("participantId", id)
    }.toString()

    fun roomStarted(startedAt: Long): String = buildJsonObject {
        put("v", 1)
        put("type", "room-started")
        put("startedAt", startedAt)
    }.toString()

    fun offer(from: String, sdp: String): String = buildJsonObject {
        put("v", 1)
        put("type", "offer")
        put("from", from)
        put("sdp", sdp)
    }.toString()

    fun answer(from: String, sdp: String): String = buildJsonObject {
        put("v", 1)
        put("type", "answer")
        put("from", from)
        put("sdp", sdp)
    }.toString()

    fun ice(from: String, candidate: String): String = buildJsonObject {
        put("v", 1)
        put("type", "ice")
        put("from", from)
        put("candidate", buildJsonObject {
            put("candidate", candidate)
            put("sdpMid", "audio")
            put("sdpMLineIndex", 0)
        })
    }.toString()
}
