package io.sauti.android.outgoing

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SautiOutgoingCallRegistryTest {

    @After
    fun tearDown() {
        SautiOutgoingCallRegistry.finishHost()
        SautiOutgoingCallRegistry.clear()
    }

    @Test
    fun beginTracksTheCallAsUnconnected() {
        SautiOutgoingCallRegistry.begin("c1")

        assertEquals("c1", SautiOutgoingCallRegistry.currentCallId)
        assertFalse(SautiOutgoingCallRegistry.connected)
        assertTrue(SautiOutgoingCallRegistry.matchesUnconnected("c1"))
    }

    @Test
    fun markConnectedMakesTheCallNoLongerMatchUnconnected() {
        SautiOutgoingCallRegistry.begin("c1")
        SautiOutgoingCallRegistry.markConnected()

        assertTrue(SautiOutgoingCallRegistry.connected)
        assertFalse(SautiOutgoingCallRegistry.matchesUnconnected("c1"))
    }

    @Test
    fun matchesUnconnectedRejectsBlankAndForeignIds() {
        SautiOutgoingCallRegistry.begin("c1")

        assertFalse(SautiOutgoingCallRegistry.matchesUnconnected(""))
        assertFalse(SautiOutgoingCallRegistry.matchesUnconnected("other"))
    }

    @Test
    fun clearForgetsTheCall() {
        SautiOutgoingCallRegistry.begin("c1")
        SautiOutgoingCallRegistry.clear()

        assertNull(SautiOutgoingCallRegistry.currentCallId)
        assertFalse(SautiOutgoingCallRegistry.connected)
        assertFalse(SautiOutgoingCallRegistry.matchesUnconnected("c1"))
    }

    @Test
    fun beginResetsConnectedForANewCall() {
        SautiOutgoingCallRegistry.begin("c1")
        SautiOutgoingCallRegistry.markConnected()
        SautiOutgoingCallRegistry.begin("c2")

        assertFalse(SautiOutgoingCallRegistry.connected)
        assertTrue(SautiOutgoingCallRegistry.matchesUnconnected("c2"))
    }

    @Test
    fun finishHostInvokesTheRegisteredFinisherOnce() {
        var count = 0
        SautiOutgoingCallRegistry.registerFinisher { count++ }

        SautiOutgoingCallRegistry.finishHost()
        SautiOutgoingCallRegistry.finishHost()

        assertEquals(1, count)
    }

    @Test
    fun finishBeforeRegisterFiresTheLaterFinisherImmediately() {
        var count = 0
        SautiOutgoingCallRegistry.finishHost()
        SautiOutgoingCallRegistry.registerFinisher { count++ }

        assertEquals(1, count)
    }

    @Test
    fun latchIsConsumedSoALaterRegisterDoesNotFireAgain() {
        var first = 0
        var second = 0
        SautiOutgoingCallRegistry.finishHost()
        SautiOutgoingCallRegistry.registerFinisher { first++ }
        SautiOutgoingCallRegistry.registerFinisher { second++ }

        assertEquals(1, first)
        assertEquals(0, second)
    }

    @Test
    fun clearResetsThePendingFinishLatch() {
        var count = 0
        SautiOutgoingCallRegistry.finishHost()
        SautiOutgoingCallRegistry.clear()
        SautiOutgoingCallRegistry.registerFinisher { count++ }

        assertEquals(0, count)
    }

    @Test
    fun unregisterWithMatchingFinisherPreventsFinish() {
        var count = 0
        val finisher = SautiOutgoingCallRegistry.Finisher { count++ }
        SautiOutgoingCallRegistry.registerFinisher(finisher)

        SautiOutgoingCallRegistry.unregisterFinisher(finisher)
        SautiOutgoingCallRegistry.finishHost()

        assertEquals(0, count)
    }

    @Test
    fun unregisterWithStaleFinisherKeepsTheLiveOne() {
        var live = 0
        val stale = SautiOutgoingCallRegistry.Finisher { }
        val current = SautiOutgoingCallRegistry.Finisher { live++ }
        SautiOutgoingCallRegistry.registerFinisher(stale)
        SautiOutgoingCallRegistry.registerFinisher(current)

        SautiOutgoingCallRegistry.unregisterFinisher(stale)
        SautiOutgoingCallRegistry.finishHost()

        assertEquals(1, live)
    }

    @Test
    fun concurrentMatchesUnconnectedIsStable() {
        val threads = 32
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)
        val matched = AtomicInteger(0)
        SautiOutgoingCallRegistry.begin("race")
        repeat(threads) {
            pool.execute {
                start.await()
                if (SautiOutgoingCallRegistry.matchesUnconnected("race")) {
                    matched.incrementAndGet()
                }
                done.countDown()
            }
        }
        start.countDown()
        done.await(5, TimeUnit.SECONDS)
        pool.shutdown()

        assertEquals(threads, matched.get())
    }
}
