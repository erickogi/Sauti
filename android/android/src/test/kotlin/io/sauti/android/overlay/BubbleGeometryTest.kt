package io.sauti.android.overlay

import kotlin.test.Test
import kotlin.test.assertEquals

class BubbleGeometryTest {

    private val bounds = BubbleBounds(
        screenWidth = 1000,
        screenHeight = 2000,
        bubbleWidth = 100,
        bubbleHeight = 100,
        margin = 20
    )

    @Test
    fun clampLeavesInRangePositionUntouched() {
        assertEquals(BubblePosition(400, 500), clampToBounds(BubblePosition(400, 500), bounds))
    }

    @Test
    fun clampPullsNegativeToMargin() {
        assertEquals(BubblePosition(20, 20), clampToBounds(BubblePosition(-50, -80), bounds))
    }

    @Test
    fun clampPullsOvershootToMaxEdge() {
        assertEquals(BubblePosition(880, 1880), clampToBounds(BubblePosition(5000, 5000), bounds))
    }

    @Test
    fun snapSendsLeftHalfToStart() {
        assertEquals(20, snapToNearestEdge(BubblePosition(100, 300), bounds).x)
    }

    @Test
    fun snapSendsRightHalfToEnd() {
        assertEquals(880, snapToNearestEdge(BubblePosition(800, 300), bounds).x)
    }

    @Test
    fun snapMidpointTieGoesToStart() {
        val midX = (bounds.screenWidth - bounds.bubbleWidth) / 2
        assertEquals(20, snapToNearestEdge(BubblePosition(midX, 300), bounds).x)
    }

    @Test
    fun snapPreservesInRangeYButClamps() {
        assertEquals(300, snapToNearestEdge(BubblePosition(800, 300), bounds).y)
        assertEquals(20, snapToNearestEdge(BubblePosition(800, -10), bounds).y)
        assertEquals(1880, snapToNearestEdge(BubblePosition(800, 9000), bounds).y)
    }

    @Test
    fun initialPositionStartEdge() {
        assertEquals(BubblePosition(20, 20), initialPosition(bounds, Edge.Start))
    }

    @Test
    fun initialPositionEndEdge() {
        assertEquals(BubblePosition(880, 20), initialPosition(bounds, Edge.End))
    }

    @Test
    fun reclampDocksOverflowingEndPositionToNewEndEdge() {
        val narrower = bounds.copy(screenWidth = 500)
        assertEquals(BubblePosition(380, 500), reclampToBounds(BubblePosition(880, 500), narrower))
    }

    @Test
    fun reclampDocksLeftHalfPositionToStartEdgeAndClampsY() {
        assertEquals(BubblePosition(20, 1880), reclampToBounds(BubblePosition(100, 9000), bounds))
    }

    @Test
    fun reclampKeepsStartEdgePositionOnStartEdge() {
        assertEquals(BubblePosition(20, 300), reclampToBounds(BubblePosition(20, 300), bounds))
    }
}
