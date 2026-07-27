package io.sauti.android.incoming

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SautiIncomingCallRegistryTest {

    @After
    fun tearDown() {
        SautiIncomingCallRegistry.release("dedupe")
        SautiIncomingCallRegistry.release("reuse")
        SautiIncomingCallRegistry.release("race")
    }

    @Test
    fun secondPresentForSameIdIsSuppressed() {
        assertTrue(SautiIncomingCallRegistry.shouldPresent("dedupe"))
        assertFalse(SautiIncomingCallRegistry.shouldPresent("dedupe"))
    }

    @Test
    fun releaseReEnablesPresentation() {
        assertTrue(SautiIncomingCallRegistry.shouldPresent("reuse"))
        SautiIncomingCallRegistry.release("reuse")
        assertTrue(SautiIncomingCallRegistry.shouldPresent("reuse"))
    }

    @Test
    fun blankCallIdIsRejected() {
        assertFalse(SautiIncomingCallRegistry.shouldPresent(""))
    }

    @Test
    fun finishInvokesRegisteredFinisherOnceAndRemovesIt() {
        var count = 0
        SautiIncomingCallRegistry.register("fin", { count++ })

        SautiIncomingCallRegistry.finish("fin")
        SautiIncomingCallRegistry.finish("fin")

        assertEquals(1, count)
    }

    @Test
    fun unregisterWithMatchingFinisherPreventsFinish() {
        var count = 0
        val finisher = SautiIncomingCallRegistry.Finisher { count++ }
        SautiIncomingCallRegistry.register("unreg", finisher)

        SautiIncomingCallRegistry.unregister("unreg", finisher)
        SautiIncomingCallRegistry.finish("unreg")

        assertEquals(0, count)
    }

    @Test
    fun unregisterWithStaleFinisherKeepsTheLiveOne() {
        var live = 0
        val stale = SautiIncomingCallRegistry.Finisher { }
        val current = SautiIncomingCallRegistry.Finisher { live++ }
        SautiIncomingCallRegistry.register("reregister", stale)
        SautiIncomingCallRegistry.register("reregister", current)

        SautiIncomingCallRegistry.unregister("reregister", stale)
        SautiIncomingCallRegistry.finish("reregister")

        assertEquals(1, live)
    }

    @Test
    fun concurrentPresentAdmitsExactlyOne() {
        val threads = 32
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)
        val admitted = AtomicInteger(0)
        repeat(threads) {
            pool.execute {
                start.await()
                if (SautiIncomingCallRegistry.shouldPresent("race")) {
                    admitted.incrementAndGet()
                }
                done.countDown()
            }
        }
        start.countDown()
        done.await(5, TimeUnit.SECONDS)
        pool.shutdown()

        assertEquals(1, admitted.get())
    }
}
