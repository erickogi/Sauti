package io.sauti.android.overlay

data class BubblePosition(val x: Int, val y: Int)

data class BubbleBounds(
    val screenWidth: Int,
    val screenHeight: Int,
    val bubbleWidth: Int,
    val bubbleHeight: Int,
    val margin: Int
)

enum class Edge {
    Start,
    End
}

private fun maxX(bounds: BubbleBounds): Int =
    (bounds.screenWidth - bounds.bubbleWidth - bounds.margin).coerceAtLeast(bounds.margin)

private fun maxY(bounds: BubbleBounds): Int =
    (bounds.screenHeight - bounds.bubbleHeight - bounds.margin).coerceAtLeast(bounds.margin)

private fun edgeX(edge: Edge, bounds: BubbleBounds): Int = when (edge) {
    Edge.Start -> bounds.margin
    Edge.End -> maxX(bounds)
}

fun clampToBounds(pos: BubblePosition, bounds: BubbleBounds): BubblePosition =
    BubblePosition(
        x = pos.x.coerceIn(bounds.margin, maxX(bounds)),
        y = pos.y.coerceIn(bounds.margin, maxY(bounds))
    )

fun snapToNearestEdge(pos: BubblePosition, bounds: BubbleBounds): BubblePosition {
    val edge = if (pos.x * 2 + bounds.bubbleWidth <= bounds.screenWidth) Edge.Start else Edge.End
    return BubblePosition(
        x = edgeX(edge, bounds),
        y = pos.y.coerceIn(bounds.margin, maxY(bounds))
    )
}

fun initialPosition(bounds: BubbleBounds, edge: Edge): BubblePosition =
    BubblePosition(x = edgeX(edge, bounds), y = bounds.margin)

fun reclampToBounds(pos: BubblePosition, bounds: BubbleBounds): BubblePosition =
    snapToNearestEdge(clampToBounds(pos, bounds), bounds)
