package io.sauti.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals

class PeerCloseTest {

    private val noopSignal = object : PeerSignal {
        override fun send(text: String) {}
        override fun onRemoteAudio(peerId: String) {}
        override fun onUnrecoverable(peerId: String) {}
        override fun onConnected(peerId: String) {}
        override fun onError(error: Throwable) {}
    }

    private fun peer(pc: FakePeerConnection): Peer =
        Peer("peer-b", "self-a", pc, CoroutineScope(Dispatchers.Unconfined), noopSignal)

    @Test
    fun closeDisposesThePeerConnectionExactlyOnce() {
        val pc = FakePeerConnection()
        val peer = peer(pc)

        peer.close()
        peer.close()

        assertEquals(1, pc.closeCount)
    }
}
