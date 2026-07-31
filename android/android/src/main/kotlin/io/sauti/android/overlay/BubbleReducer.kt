package io.sauti.android.overlay

import io.sauti.engine.CallPhase

enum class BubbleVisibility {
    Shown,
    Hidden
}

object BubbleReducer {
    fun reduce(
        callActive: Boolean,
        appForeground: Boolean,
        permissionGranted: Boolean,
        optedIn: Boolean
    ): BubbleVisibility =
        if (optedIn && permissionGranted && callActive && !appForeground) {
            BubbleVisibility.Shown
        } else {
            BubbleVisibility.Hidden
        }
}

fun bubbleCallActive(phase: CallPhase): Boolean =
    phase == CallPhase.CONNECTING || phase == CallPhase.CONNECTED
