package io.sauti.ui.compose.overlay

object CallBarReducer {

    fun visible(optedIn: Boolean, callActive: Boolean, onCallScreen: Boolean): Boolean =
        optedIn && callActive && !onCallScreen
}
