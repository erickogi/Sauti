package io.sauti.android.overlay

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BubbleContrastTest {

    @Test
    fun darkFillsGetLightTone() {
        assertEquals(ContrastTone.Light, contrastToneOn(0, 0, 0))
        assertEquals(ContrastTone.Light, contrastToneOn(21, 101, 192))
    }

    @Test
    fun lightFillsGetDarkTone() {
        assertEquals(ContrastTone.Dark, contrastToneOn(255, 255, 255))
        assertEquals(ContrastTone.Dark, contrastToneOn(240, 230, 210))
    }

    @Test
    fun toneFlipsAroundThePivot() {
        assertEquals(ContrastTone.Light, contrastToneOn(110, 110, 110))
        assertEquals(ContrastTone.Dark, contrastToneOn(130, 130, 130))
    }

    @Test
    fun luminanceIsMonotonicAcrossGrays() {
        assertTrue(relativeLuminance(0, 0, 0) < relativeLuminance(128, 128, 128))
        assertTrue(relativeLuminance(128, 128, 128) < relativeLuminance(255, 255, 255))
    }

    @Test
    fun channelsAreClampedToRange() {
        assertEquals(relativeLuminance(255, 255, 255), relativeLuminance(300, 300, 300), 1e-9)
        assertEquals(relativeLuminance(0, 0, 0), relativeLuminance(-10, -10, -10), 1e-9)
    }
}
