package io.sauti.android.overlay

import kotlin.test.Test
import kotlin.test.assertEquals

class BubbleExpansionReducerTest {

    @Test
    fun tapTogglesCollapsedToExpanded() {
        assertEquals(BubbleExpansion.Expanded, BubbleExpansionReducer.onTap(BubbleExpansion.Collapsed))
    }

    @Test
    fun tapTogglesExpandedToCollapsed() {
        assertEquals(BubbleExpansion.Collapsed, BubbleExpansionReducer.onTap(BubbleExpansion.Expanded))
    }

    @Test
    fun dragStartCollapses() {
        assertEquals(BubbleExpansion.Collapsed, BubbleExpansionReducer.onDragStart(BubbleExpansion.Expanded))
        assertEquals(BubbleExpansion.Collapsed, BubbleExpansionReducer.onDragStart(BubbleExpansion.Collapsed))
    }

    @Test
    fun returnCollapses() {
        assertEquals(BubbleExpansion.Collapsed, BubbleExpansionReducer.onReturn())
    }

    @Test
    fun actionOutsideCollapses() {
        assertEquals(BubbleExpansion.Collapsed, BubbleExpansionReducer.onActionOutside())
    }

    @Test
    fun hiddenCollapses() {
        assertEquals(BubbleExpansion.Collapsed, BubbleExpansionReducer.onHidden())
    }
}
