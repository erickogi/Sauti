package io.sauti.engine

import kotlinx.coroutines.CoroutineScope

interface MeshHooks {
    fun send(text: String)
    fun onConnected(peerId: String)
    fun onRemoteAudio(peerId: String)
    fun onError(error: Throwable)
}

class Mesh(
    private val scope: CoroutineScope,
    private val factory: RtcFactory,
    private val selfId: String,
    private val hooks: MeshHooks
) {
    private val peers = LinkedHashMap<String, Peer>()
    private var iceServers: List<IceServer> = emptyList()

    private val signal = object : PeerSignal {
        override fun send(text: String) = hooks.send(text)
        override fun onRemoteAudio(peerId: String) = hooks.onRemoteAudio(peerId)
        override fun onUnrecoverable(peerId: String) = rebuild(peerId)
        override fun onConnected(peerId: String) = hooks.onConnected(peerId)
        override fun onError(error: Throwable) = hooks.onError(error)
    }

    fun setIceServers(servers: List<IceServer>) {
        iceServers = servers
    }

    fun has(peerId: String): Boolean = peers.containsKey(peerId)

    fun ids(): List<String> = peers.keys.toList()

    fun count(): Int = peers.size

    fun ensurePeer(peerId: String): Peer = peers[peerId] ?: createPeer(peerId)

    private fun createPeer(peerId: String): Peer {
        val pc = factory.createPeerConnection(iceServers)
        val peer = Peer(peerId, selfId, pc, scope, signal)
        peers[peerId] = peer
        return peer
    }

    fun addPeer(peerId: String): Peer {
        val peer = createPeer(peerId)
        peer.start()
        return peer
    }

    private fun rebuild(peerId: String) {
        val old = peers[peerId] ?: return
        old.close()
        peers.remove(peerId)
        createPeer(peerId).start()
    }

    fun removePeer(peerId: String) {
        val peer = peers[peerId] ?: return
        peer.close()
        peers.remove(peerId)
    }

    suspend fun routeOffer(from: String, sdp: String) {
        ensurePeer(from).onOffer(sdp)
    }

    suspend fun routeAnswer(from: String, sdp: String) {
        peers[from]?.onAnswer(sdp)
    }

    suspend fun routeIce(from: String, candidate: IceCandidate) {
        peers[from]?.onIce(candidate)
    }

    fun iceRestartAll() {
        for (peer in peers.values) peer.iceRestart()
    }

    fun statsPeer(peerId: String): Peer? = peers[peerId]

    fun teardown() {
        for (peer in peers.values) peer.close()
        peers.clear()
    }
}
