package io.sauti.android.overlay

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BubbleGestureTest {

    private val slop = 10f

    @Test
    fun zeroTravelIsTap() {
        assertTrue(isTap(0f, 0f, slop))
        assertFalse(exceedsSlop(0f, 0f, slop))
    }

    @Test
    fun belowSlopIsTap() {
        assertTrue(isTap(5f, 5f, slop))
    }

    @Test
    fun travelExactlyEqualToSlopIsTap() {
        assertTrue(isTap(slop, 0f, slop))
        assertFalse(exceedsSlop(slop, 0f, slop))
    }

    @Test
    fun aboveSlopIsDrag() {
        assertFalse(isTap(20f, 0f, slop))
        assertTrue(exceedsSlop(20f, 0f, slop))
    }

    @Test
    fun oneAxisAboveSlopIsDrag() {
        assertTrue(exceedsSlop(0f, 15f, slop))
    }

    @Test
    fun diagonalTravelUsesEuclideanDistance() {
        assertTrue(exceedsSlop(8f, 8f, slop))
        assertFalse(exceedsSlop(7f, 7f, slop))
    }
}
