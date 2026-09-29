package io.sauti.ui.compose

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class SautiColorsTest {

    private fun baseColors(accent: Color = Color(0xFF184857)): SautiColors = SautiColors(
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF1B1B1B),
        onSurfaceMuted = Color(0xFF6B6B6B),
        accent = accent,
        danger = Color(0xFFD62D20),
        onDanger = Color(0xFFFFFFFF),
        qualityGood = Color(0xFF2E7D32),
        qualityFair = Color(0xFFF9A825),
        qualityPoor = Color(0xFFD84315)
    )

    @Test
    fun controlTokensHaveNeutralDefaults() {
        val colors = baseColors()

        assertEquals(Color(0xFFF1F1F1), colors.controlIdleBackground)
        assertEquals(Color(0xFFE3FFF1), colors.controlActiveBackground)
        assertEquals(Color(0xFF0CA67D), colors.controlActiveContent)
        assertEquals(Color(0xFF0CD39F), colors.positive)
        assertEquals(Color(0xFFFFFFFF), colors.onPositive)
        assertEquals(Color(0xFFFFFFFF), colors.onAccent)
    }

    @Test
    fun controlIdleContentDefaultsToAccent() {
        val colors = baseColors(accent = Color(0xFF123456))

        assertEquals(Color(0xFF123456), colors.controlIdleContent)
    }

    @Test
    fun explicitControlTokensAreKept() {
        val colors = baseColors().copy(
            controlIdleBackground = Color(0xFF010101),
            controlIdleContent = Color(0xFF020202),
            controlActiveBackground = Color(0xFF030303),
            controlActiveContent = Color(0xFF040404),
            positive = Color(0xFF050505),
            onPositive = Color(0xFF060606),
            onAccent = Color(0xFF070707)
        )

        assertEquals(Color(0xFF010101), colors.controlIdleBackground)
        assertEquals(Color(0xFF020202), colors.controlIdleContent)
        assertEquals(Color(0xFF030303), colors.controlActiveBackground)
        assertEquals(Color(0xFF040404), colors.controlActiveContent)
        assertEquals(Color(0xFF050505), colors.positive)
        assertEquals(Color(0xFF060606), colors.onPositive)
        assertEquals(Color(0xFF070707), colors.onAccent)
    }
}
