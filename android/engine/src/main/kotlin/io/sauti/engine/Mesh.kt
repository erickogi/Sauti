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

    fun ensurePeer(peerId: String): Peer {
        synchronized(lock) { peers[peerId]?.let { return it } }
        val fresh = createDetached(peerId)
        var loser: Peer? = null
        val result = synchronized(lock) {
            val existing = peers[peerId]
            if (existing != null) {
                loser = fresh
                existing
            } else {
                peers[peerId] = fresh
                fresh
            }
        }
        loser?.close()
        return result
    }

    fun addPeer(peerId: String): Peer {
        val fresh = createDetached(peerId)
        var loser: Peer? = null
        var created = false
        val result = synchronized(lock) {
            val existing = peers[peerId]
            if (existing != null) {
                loser = fresh
                existing
            } else {
                peers[peerId] = fresh
                created = true
                fresh
            }
        }
        loser?.close()
        if (created) result.start()
        return result
    }

    private fun createDetached(peerId: String): Peer {
        val servers = synchronized(lock) { iceServers }
        return Peer(peerId, selfId, factory.createPeerConnection(servers), scope, signal)
    }

    private fun rebuild(peerId: String) {
        val servers = synchronized(lock) {
            if (!peers.containsKey(peerId)) return
            iceServers
        }
        val fresh = Peer(peerId, selfId, factory.createPeerConnection(servers), scope, signal)
        var old: Peer? = null
        val installed = synchronized(lock) {
            val existing = peers[peerId]
            if (existing != null) {
                old = existing
                peers[peerId] = fresh
                true
            } else {
                false
            }
        }
        old?.close()
        if (installed) fresh.start() else fresh.close()
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
        for (peer in snapshot) runCatching { peer.iceRestart() }
    }

    fun statsPeer(peerId: String): Peer? = synchronized(lock) { peers[peerId] }

    fun teardown() {
        val snapshot = synchronized(lock) {
            val all = peers.values.toList()
            peers.clear()
            all
        }
        for (peer in snapshot) runCatching { peer.close() }
    }
}
