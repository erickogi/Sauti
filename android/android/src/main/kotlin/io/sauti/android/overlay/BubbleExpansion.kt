package io.sauti.android.overlay

enum class BubbleExpansion {
    Collapsed,
    Expanded
}

object BubbleExpansionReducer {
    fun onTap(current: BubbleExpansion): BubbleExpansion =
        if (current == BubbleExpansion.Expanded) BubbleExpansion.Collapsed else BubbleExpansion.Expanded

    fun onDragStart(current: BubbleExpansion): BubbleExpansion = BubbleExpansion.Collapsed

    fun onReturn(): BubbleExpansion = BubbleExpansion.Collapsed

    fun onActionOutside(): BubbleExpansion = BubbleExpansion.Collapsed

    fun onHidden(): BubbleExpansion = BubbleExpansion.Collapsed
}
