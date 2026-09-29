package io.sauti.ui.compose.overlay

object OverlayPromptDecision {

    fun shouldPrompt(optedIn: Boolean, granted: Boolean, dismissed: Boolean): Boolean =
        optedIn && !granted && !dismissed
}
