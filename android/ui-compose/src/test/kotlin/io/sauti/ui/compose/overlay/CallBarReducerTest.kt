package io.sauti.ui.compose.overlay

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CallBarReducerTest {

    @Test
    fun visibleWhenCallActiveAndNotOnTheCallScreen() {
        assertTrue(CallBarReducer.visible(optedIn = true, callActive = true, onCallScreen = false))
    }

    @Test
    fun hiddenWhileOnTheCallScreen() {
        assertFalse(CallBarReducer.visible(optedIn = true, callActive = true, onCallScreen = true))
    }

    @Test
    fun hiddenWhenNoActiveCall() {
        assertFalse(CallBarReducer.visible(optedIn = true, callActive = false, onCallScreen = false))
    }

    @Test
    fun hiddenWhenNotOptedIn() {
        assertFalse(CallBarReducer.visible(optedIn = false, callActive = true, onCallScreen = false))
    }
}
