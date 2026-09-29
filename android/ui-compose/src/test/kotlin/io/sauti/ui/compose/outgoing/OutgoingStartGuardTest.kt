package io.sauti.ui.compose.outgoing

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OutgoingStartGuardTest {

    @Test
    fun proceedsWhenGenerationUnchanged() {
        assertTrue(OutgoingStartGuard.shouldProceedAfterStart(capturedGeneration = 7, currentGeneration = 7))
    }

    @Test
    fun abortsWhenGenerationAdvanced() {
        assertFalse(OutgoingStartGuard.shouldProceedAfterStart(capturedGeneration = 7, currentGeneration = 8))
    }

    @Test
    fun abortsWhenTornDownAndRestarted() {
        assertFalse(OutgoingStartGuard.shouldProceedAfterStart(capturedGeneration = 3, currentGeneration = 5))
    }

    @Test
    fun abortsWhenGenerationRegressed() {
        assertFalse(OutgoingStartGuard.shouldProceedAfterStart(capturedGeneration = 9, currentGeneration = 4))
    }
}
