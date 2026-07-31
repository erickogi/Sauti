package io.sauti.ui.compose.overlay

import io.sauti.ui.compose.SautiCallUiState

interface BubbleOverlayHost {
    fun show(uiState: SautiCallUiState)
    fun hide()
}
