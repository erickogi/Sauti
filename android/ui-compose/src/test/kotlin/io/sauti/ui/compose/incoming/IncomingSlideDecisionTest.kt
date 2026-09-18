package io.sauti.ui.compose.incoming

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IncomingSlideDecisionTest {

    @Test
    fun acceptsWhenDraggedPastThreshold() {
        assertTrue(IncomingSlideDecision.accepts(offsetPx = 70f, travelPx = 100f))
    }

    @Test
    fun springsBackJustUnderThreshold() {
        assertFalse(IncomingSlideDecision.accepts(offsetPx = 59f, travelPx = 100f))
    }

    @Test
    fun acceptsJustPastThreshold() {
        assertTrue(IncomingSlideDecision.accepts(offsetPx = 61f, travelPx = 100f))
    }

    @Test
    fun neverAcceptsWithoutTravel() {
        assertFalse(IncomingSlideDecision.accepts(offsetPx = 0f, travelPx = 0f))
    }

    @Test
    fun springsBackAtRest() {
        assertFalse(IncomingSlideDecision.accepts(offsetPx = 0f, travelPx = 100f))
    }
}
