package io.sauti.android.overlay

import io.sauti.engine.CallPhase
import kotlin.test.Test
import kotlin.test.assertEquals

class BubbleReducerTest {

    private val bools = listOf(true, false)

    @Test
    fun shownOnlyWhenCallActiveBackgroundedGrantedAndOptedIn() {
        for (callActive in bools) {
            for (appForeground in bools) {
                for (permissionGranted in bools) {
                    for (optedIn in bools) {
                        val expected =
                            if (callActive && !appForeground && permissionGranted && optedIn) {
                                BubbleVisibility.Shown
                            } else {
                                BubbleVisibility.Hidden
                            }
                        assertEquals(
                            expected,
                            BubbleReducer.reduce(callActive, appForeground, permissionGranted, optedIn)
                        )
                    }
                }
            }
        }
    }

    @Test
    fun theSingleShownCombination() {
        assertEquals(
            BubbleVisibility.Shown,
            BubbleReducer.reduce(callActive = true, appForeground = false, permissionGranted = true, optedIn = true)
        )
    }

    @Test
    fun callActiveMapsConnectingAndConnected() {
        assertEquals(true, bubbleCallActive(CallPhase.CONNECTING))
        assertEquals(true, bubbleCallActive(CallPhase.CONNECTED))
    }

    @Test
    fun callInactiveForIdleAndLeft() {
        assertEquals(false, bubbleCallActive(CallPhase.IDLE))
        assertEquals(false, bubbleCallActive(CallPhase.LEFT))
    }

    @Test
    fun callEndsWhileBackgroundedHides() {
        assertEquals(
            BubbleVisibility.Shown,
            BubbleReducer.reduce(callActive = true, appForeground = false, permissionGranted = true, optedIn = true)
        )
        assertEquals(
            BubbleVisibility.Hidden,
            BubbleReducer.reduce(callActive = false, appForeground = false, permissionGranted = true, optedIn = true)
        )
    }

    @Test
    fun appToForegroundHides() {
        assertEquals(
            BubbleVisibility.Hidden,
            BubbleReducer.reduce(callActive = true, appForeground = true, permissionGranted = true, optedIn = true)
        )
    }

    @Test
    fun permissionRevokedHides() {
        assertEquals(
            BubbleVisibility.Hidden,
            BubbleReducer.reduce(callActive = true, appForeground = false, permissionGranted = false, optedIn = true)
        )
    }

    @Test
    fun optedOutHidesRegardless() {
        for (callActive in bools) {
            for (appForeground in bools) {
                for (permissionGranted in bools) {
                    assertEquals(
                        BubbleVisibility.Hidden,
                        BubbleReducer.reduce(callActive, appForeground, permissionGranted, optedIn = false)
                    )
                }
            }
        }
    }
}
