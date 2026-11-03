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
    private val lock = Any()
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
        synchronized(lock) { iceServers = servers }
    }

    fun has(peerId: String): Boolean = synchronized(lock) { peers.containsKey(peerId) }

    fun ids(): List<String> = synchronized(lock) { peers.keys.toList() }

    fun count(): Int = synchronized(lock) { peers.size }

    fun ensurePeer(peerId: String): Peer = synchronized(lock) { peers[peerId] ?: createPeerLocked(peerId) }

    private fun createPeerLocked(peerId: String): Peer {
        val pc = factory.createPeerConnection(iceServers)
        val peer = Peer(peerId, selfId, pc, scope, signal)
        peers[peerId] = peer
        return peer
    }

    fun addPeer(peerId: String): Peer {
        val peer = synchronized(lock) { createPeerLocked(peerId) }
        peer.start()
        return peer
    }

    private fun rebuild(peerId: String) {
        val pair = synchronized(lock) {
            val old = peers[peerId] ?: return
            peers.remove(peerId)
            old to createPeerLocked(peerId)
        }
        pair.first.close()
        pair.second.start()
    }

    fun removePeer(peerId: String) {
        val peer = synchronized(lock) { peers.remove(peerId) } ?: return
        peer.close()
    }

    suspend fun routeOffer(from: String, sdp: String) {
        ensurePeer(from).onOffer(sdp)
    }

    suspend fun routeAnswer(from: String, sdp: String) {
        peerOf(from)?.onAnswer(sdp)
    }

    suspend fun routeIce(from: String, candidate: IceCandidate) {
        peerOf(from)?.onIce(candidate)
    }

    private fun peerOf(peerId: String): Peer? = synchronized(lock) { peers[peerId] }

    fun iceRestartAll() {
        val snapshot = synchronized(lock) { peers.values.toList() }
        for (peer in snapshot) peer.iceRestart()
    }

    fun statsPeer(peerId: String): Peer? = synchronized(lock) { peers[peerId] }

    fun teardown() {
        val snapshot = synchronized(lock) {
            val all = peers.values.toList()
            peers.clear()
            all
        }
        for (peer in snapshot) peer.close()
    }
}
