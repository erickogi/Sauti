package io.sauti.engine

import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class RecordingSignal : PeerSignal {
    val sent = mutableListOf<String>()
    val errors = mutableListOf<Throwable>()
    override fun send(text: String) {
        sent += text
    }

    override fun onRemoteAudio(peerId: String) = Unit
    override fun onUnrecoverable(peerId: String) = Unit
    override fun onConnected(peerId: String) = Unit
    override fun onError(error: Throwable) {
        errors += error
    }

    fun typesTo(): List<String> = sent.map { FakeTransportHub.typeOf(it) }
}

class NegotiationTest {
    @Test
    fun politeAndImpoliteDeriveOppositeRoles() = runTest {
        val impolitePc = FakePeerConnection()
        val impolite = Peer(peerId = "a", selfId = "z", pc = impolitePc, scope = backgroundScope, signal = RecordingSignal())
        val politePc = FakePeerConnection()
        val polite = Peer(peerId = "z", selfId = "a", pc = politePc, scope = backgroundScope, signal = RecordingSignal())

        assertFalse(impolite.polite)
        assertTrue(polite.polite)
    }

    @Test
    fun glareConvergesWithoutDeadlock() = runTest {
        val politeSignal = RecordingSignal()
        val politePc = FakePeerConnection()
        val polite = Peer(peerId = "z", selfId = "a", pc = politePc, scope = backgroundScope, signal = politeSignal)

        val impoliteSignal = RecordingSignal()
        val impolitePc = FakePeerConnection()
        val impolite = Peer(peerId = "a", selfId = "z", pc = impolitePc, scope = backgroundScope, signal = impoliteSignal)

        politePc.signalingState = "have-local-offer"
        impolitePc.signalingState = "have-local-offer"

        polite.onOffer("remote-offer-for-polite")
        runCurrent()
        impolite.onOffer("remote-offer-for-impolite")
        runCurrent()

        assertEquals("stable", politePc.signalingState)
        assertEquals(listOf("answer"), politeSignal.typesTo())
        assertEquals("answer", politePc.localDescription?.type)

        assertTrue(impoliteSignal.sent.isEmpty())
        assertNull(impolitePc.remoteDescription)
    }

    @Test
    fun impoliteEmitsInitialOfferOnStart() = runTest {
        val signal = RecordingSignal()
        val pc = FakePeerConnection()
        val impolite = Peer(peerId = "a", selfId = "z", pc = pc, scope = backgroundScope, signal = signal)
        impolite.start()
        runCurrent()
        assertEquals(listOf("offer"), signal.typesTo())
        assertEquals(1, pc.createdOffers)
    }

    @Test
    fun politeStaysSilentOnStart() = runTest {
        val signal = RecordingSignal()
        val pc = FakePeerConnection()
        val polite = Peer(peerId = "z", selfId = "a", pc = pc, scope = backgroundScope, signal = signal)
        polite.start()
        runCurrent()
        assertTrue(signal.sent.isEmpty())
    }

    @Test
    fun iceRestartDrivesOfferCarryingRestartFlag() = runTest {
        val signal = RecordingSignal()
        val pc = FakePeerConnection()
        val impolite = Peer(peerId = "a", selfId = "z", pc = pc, scope = backgroundScope, signal = signal)
        impolite.start()
        runCurrent()
        assertEquals(1, pc.createdOffers)
        assertEquals(0, pc.iceRestartOffers)

        impolite.iceRestart()
        runCurrent()
        assertTrue(pc.restartIceCalls >= 1)
        assertEquals(1, pc.iceRestartOffers)
    }

    @Test
    fun failedConnectionRestartsThenRebuildsWhenUnrecoverable() = runTest {
        var unrecoverable = 0
        val signal = object : PeerSignal {
            override fun send(text: String) = Unit
            override fun onRemoteAudio(peerId: String) = Unit
            override fun onUnrecoverable(peerId: String) {
                unrecoverable += 1
            }
            override fun onConnected(peerId: String) = Unit
            override fun onError(error: Throwable) = Unit
        }
        val pc = FakePeerConnection()
        val peer = Peer(peerId = "a", selfId = "z", pc = pc, scope = backgroundScope, signal = signal)
        peer.start()
        runCurrent()

        pc.emitFailed()
        runCurrent()
        assertTrue(pc.restartIceCalls >= 1)
        assertEquals(0, unrecoverable)

        pc.emitFailed()
        runCurrent()
        assertEquals(1, unrecoverable)
    }
}
