package io.sauti.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MeshConcurrencyTest {

    private val hooks = object : MeshHooks {
        override fun send(text: String) {}
        override fun onConnected(peerId: String) {}
        override fun onRemoteAudio(peerId: String) {}
        override fun onError(error: Throwable) {}
    }

    private class GatedRtcFactory : RtcFactory {
        val created = CopyOnWriteArrayList<FakePeerConnection>()
        private val gate = CyclicBarrier(2)

        override fun createPeerConnection(iceServers: List<IceServer>): PeerConnectionPort {
            runCatching { gate.await(500, TimeUnit.MILLISECONDS) }
            val pc = FakePeerConnection()
            created += pc
            return pc
        }
    }

    private fun mesh(factory: RtcFactory): Mesh =
        Mesh(CoroutineScope(Dispatchers.Unconfined), factory, "self", hooks)

    @Test
    fun concurrentEnsurePeerForOneIdCreatesExactlyOnePeerConnection() {
        val factory = GatedRtcFactory()
        val mesh = mesh(factory)
        val start = CyclicBarrier(2)

        val a = thread { start.await(); mesh.ensurePeer("x") }
        val b = thread { start.await(); mesh.ensurePeer("x") }
        a.join()
        b.join()

        assertEquals(1, factory.created.size, "ensurePeer raced into a second, leaked PeerConnection")
        assertTrue(mesh.has("x"))
    }

    @Test
    fun addRemoveAndTeardownUnderContentionLeaveTheMapEmptyAndThrowNothing() {
        val mesh = mesh(FakeRtcFactory())
        val failures = CopyOnWriteArrayList<Throwable>()
        val rounds = 2_000
        val barrier = CyclicBarrier(3)

        val mutator = thread {
            barrier.await()
            runCatching {
                repeat(rounds) { i ->
                    val id = "peer-${i % 8}"
                    mesh.addPeer(id)
                    mesh.removePeer(id)
                }
            }.onFailure { failures += it }
        }
        val iterator = thread {
            barrier.await()
            runCatching {
                repeat(rounds) {
                    mesh.ids()
                    mesh.iceRestartAll()
                    mesh.count()
                }
            }.onFailure { failures += it }
        }
        val reaper = thread {
            barrier.await()
            runCatching { repeat(rounds) { mesh.teardown() } }.onFailure { failures += it }
        }

        mutator.join()
        iterator.join()
        reaper.join()

        assertTrue(failures.isEmpty(), "concurrent access threw: ${failures.joinToString { it.toString() }}")
        mesh.teardown()
        assertEquals(0, mesh.count())
    }
}
