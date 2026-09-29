package io.sauti.android.overlay

fun exceedsSlop(dx: Float, dy: Float, slopPx: Float): Boolean = dx * dx + dy * dy > slopPx * slopPx

fun isTap(totalDx: Float, totalDy: Float, slopPx: Float): Boolean = !exceedsSlop(totalDx, totalDy, slopPx)
