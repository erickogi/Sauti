package io.sauti.android.incoming

import java.util.concurrent.CyclicBarrier
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals

class SautiIncomingCallRegistryRaceTest {

    @Test
    fun concurrentRegisterAndCancelAlwaysFinishesExactlyOnce() {
        val rounds = 5_000
        val stranded = AtomicInteger(0)
        val doubled = AtomicInteger(0)

        for (i in 0 until rounds) {
            val id = "race-$i"
            val finishes = AtomicInteger(0)
            val barrier = CyclicBarrier(2)

            val register = thread {
                barrier.await()
                SautiIncomingCallRegistry.register(id) { finishes.incrementAndGet() }
            }
            val cancel = thread {
                barrier.await()
                SautiIncomingCallRegistry.cancel(id)
            }
            register.join()
            cancel.join()

            when (finishes.get()) {
                0 -> stranded.incrementAndGet()
                1 -> Unit
                else -> doubled.incrementAndGet()
            }
            SautiIncomingCallRegistry.release(id)
        }

        assertEquals(0, stranded.get(), "a cancelled call left its finisher uninvoked")
        assertEquals(0, doubled.get(), "a cancelled call invoked its finisher more than once")
    }
}
