package io.sauti.ui.compose.overlay

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OverlayPromptDecisionTest {

    @Test
    fun promptsWhenOptedInUngrantedAndNotDismissed() {
        assertTrue(OverlayPromptDecision.shouldPrompt(optedIn = true, granted = false, dismissed = false))
    }

    @Test
    fun doesNotPromptWhenBubbleNotOptedIn() {
        assertFalse(OverlayPromptDecision.shouldPrompt(optedIn = false, granted = false, dismissed = false))
    }

    @Test
    fun doesNotPromptWhenPermissionAlreadyGranted() {
        assertFalse(OverlayPromptDecision.shouldPrompt(optedIn = true, granted = true, dismissed = false))
    }

    @Test
    fun doesNotPromptAgainAfterUserDismissed() {
        assertFalse(OverlayPromptDecision.shouldPrompt(optedIn = true, granted = false, dismissed = true))
    }
}
