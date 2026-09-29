package io.sauti.android.incoming

import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SautiIncomingCallRegistryCancelTest {

    @After
    fun tearDown() {
        SautiIncomingCallRegistry.release("buffered")
        SautiIncomingCallRegistry.release("late")
        SautiIncomingCallRegistry.release("present")
        SautiIncomingCallRegistry.release("recycle")
    }

    @Test
    fun cancelBeforeRegisterFinishesImmediatelyOnRegister() {
        var finished = 0
        SautiIncomingCallRegistry.cancel("buffered")

        SautiIncomingCallRegistry.register("buffered") { finished++ }

        assertEquals(1, finished)
    }

    @Test
    fun cancelBeforePresentRejectsPresentation() {
        SautiIncomingCallRegistry.cancel("present")
        assertFalse(SautiIncomingCallRegistry.shouldPresent("present"))
    }

    @Test
    fun cancelAfterRegisterFinishesTheLiveFinisher() {
        var finished = 0
        SautiIncomingCallRegistry.register("late") { finished++ }

        SautiIncomingCallRegistry.cancel("late")

        assertEquals(1, finished)
    }

    @Test
    fun releaseClearsCancelledSoIdCanRering() {
        SautiIncomingCallRegistry.cancel("recycle")
        assertFalse(SautiIncomingCallRegistry.shouldPresent("recycle"))

        SautiIncomingCallRegistry.release("recycle")

        assertTrue(SautiIncomingCallRegistry.shouldPresent("recycle"))
    }

    @Test
    fun blankCancelIsIgnored() {
        var finished = 0
        SautiIncomingCallRegistry.cancel("")
        SautiIncomingCallRegistry.register("late") { finished++ }
        assertEquals(0, finished)
    }

    @Test
    fun cancelWithoutPresentEvictsOldestBeyondCap() {
        val cap = 256
        val total = cap + 64
        try {
            repeat(total) { SautiIncomingCallRegistry.cancel("bound-$it") }

            assertTrue(SautiIncomingCallRegistry.shouldPresent("bound-0"))
            assertFalse(SautiIncomingCallRegistry.shouldPresent("bound-${total - 1}"))
        } finally {
            repeat(total) { SautiIncomingCallRegistry.release("bound-$it") }
        }
    }
}
