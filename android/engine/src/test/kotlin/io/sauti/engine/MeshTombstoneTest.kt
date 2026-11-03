package io.sauti.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MeshTombstoneTest {

    private val hooks = object : MeshHooks {
        override fun send(text: String) {}
        override fun onConnected(peerId: String) {}
        override fun onRemoteAudio(peerId: String) {}
        override fun onError(error: Throwable) {}
    }

    private fun mesh(factory: FakeRtcFactory): Mesh =
        Mesh(CoroutineScope(Dispatchers.Unconfined), factory, "self", hooks)

    @Test
    fun offerAfterParticipantLeftDoesNotResurrectThePeer() = runBlocking {
        val factory = FakeRtcFactory()
        val mesh = mesh(factory)
        mesh.addPeer("B")
        mesh.removePeer("B")

        mesh.routeOffer("B", "offer-sdp")

        assertFalse(mesh.has("B"), "a left peer must not be recreated by a late offer")
        assertEquals(1, factory.created.size, "no new PeerConnection should be created for a left peer")
    }

    @Test
    fun offerBeforeJoinStillCreatesThePeer() = runBlocking {
        val factory = FakeRtcFactory()
        val mesh = mesh(factory)

        mesh.routeOffer("C", "offer-sdp")

        assertTrue(mesh.has("C"), "an offer for an unseen peer should still create it")
        assertEquals(1, factory.created.size)
    }

    @Test
    fun rejoinAfterLeaveClearsTheTombstone() = runBlocking {
        val factory = FakeRtcFactory()
        val mesh = mesh(factory)
        mesh.addPeer("B")
        mesh.removePeer("B")

        mesh.addPeer("B")
        mesh.routeOffer("B", "offer-sdp")

        assertTrue(mesh.has("B"))
    }
}
