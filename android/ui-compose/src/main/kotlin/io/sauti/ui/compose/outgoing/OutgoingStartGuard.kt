package io.sauti.ui.compose.outgoing

internal object OutgoingStartGuard {

    fun shouldProceedAfterStart(capturedGeneration: Int, currentGeneration: Int): Boolean =
        capturedGeneration == currentGeneration
}
