package io.sauti.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class MeshTeardownTest {

    private val hooks = object : MeshHooks {
        override fun send(text: String) {}
        override fun onConnected(peerId: String) {}
        override fun onRemoteAudio(peerId: String) {}
        override fun onError(error: Throwable) {}
    }

    private fun mesh(factory: FakeRtcFactory): Mesh =
        Mesh(CoroutineScope(Dispatchers.Unconfined), factory, "self", hooks)

    @Test
    fun removeThenTeardownClosesEachPeerExactlyOnce() {
        val factory = FakeRtcFactory()
        val mesh = mesh(factory)
        mesh.addPeer("peer-b")
        mesh.addPeer("peer-c")

        mesh.removePeer("peer-b")
        mesh.teardown()

        assertEquals(2, factory.created.size)
        assertEquals(listOf(1, 1), factory.created.map { it.closeCount })
        assertEquals(0, mesh.count())
    }

    @Test
    fun teardownClosesEveryPeerEvenWhenOneCloseThrows() {
        val factory = FakeRtcFactory()
        val mesh = mesh(factory)
        mesh.addPeer("peer-b")
        mesh.addPeer("peer-c")
        factory.created.forEach { it.throwOnClose = true }

        mesh.teardown()

        assertEquals(listOf(1, 1), factory.created.map { it.closeCount })
        assertEquals(0, mesh.count())
    }

    @Test
    fun teardownIsIdempotent() {
        val factory = FakeRtcFactory()
        val mesh = mesh(factory)
        mesh.addPeer("peer-b")

        mesh.teardown()
        mesh.teardown()

        assertEquals(1, factory.created.single().closeCount)
        assertFalse(mesh.has("peer-b"))
    }
}
