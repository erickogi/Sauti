package io.sauti.ui.compose.incoming

object IncomingSlideDecision {

    const val THRESHOLD_FRACTION: Float = 0.6f

    fun accepts(offsetPx: Float, travelPx: Float, thresholdFraction: Float = THRESHOLD_FRACTION): Boolean =
        travelPx > 0f && offsetPx >= travelPx * thresholdFraction
}
