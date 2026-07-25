package io.sauti.ui.compose

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.sauti.engine.Quality

@Immutable
data class SautiColors(
    val surface: Color,
    val onSurface: Color,
    val onSurfaceMuted: Color,
    val accent: Color,
    val danger: Color,
    val onDanger: Color,
    val qualityGood: Color,
    val qualityFair: Color,
    val qualityPoor: Color
) {
    fun colorFor(quality: Quality): Color = when (quality) {
        Quality.GOOD -> qualityGood
        Quality.DEGRADED -> qualityFair
        Quality.POOR -> qualityPoor
    }
}

@Immutable
data class SautiTypography(
    val status: TextStyle,
    val timer: TextStyle,
    val label: TextStyle,
    val caption: TextStyle
)

@Immutable
data class SautiShapes(
    val control: RoundedCornerShape,
    val surface: RoundedCornerShape
)

@Composable
@ReadOnlyComposable
private fun defaultColors(): SautiColors = SautiColors(
    surface = MaterialTheme.colorScheme.surface,
    onSurface = MaterialTheme.colorScheme.onSurface,
    onSurfaceMuted = MaterialTheme.colorScheme.onSurfaceVariant,
    accent = MaterialTheme.colorScheme.primary,
    danger = MaterialTheme.colorScheme.error,
    onDanger = MaterialTheme.colorScheme.onError,
    qualityGood = Color(0xFF2E7D32),
    qualityFair = Color(0xFFF9A825),
    qualityPoor = Color(0xFFD84315)
)

@Composable
@ReadOnlyComposable
private fun defaultTypography(): SautiTypography = SautiTypography(
    status = MaterialTheme.typography.titleMedium,
    timer = MaterialTheme.typography.headlineSmall,
    label = MaterialTheme.typography.bodyLarge,
    caption = MaterialTheme.typography.bodySmall
)

private fun defaultShapes(): SautiShapes = SautiShapes(
    control = RoundedCornerShape(28.dp),
    surface = RoundedCornerShape(16.dp)
)

private val fallbackColors = SautiColors(
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B1B1B),
    onSurfaceMuted = Color(0xFF6B6B6B),
    accent = Color(0xFF1565C0),
    danger = Color(0xFFD32F2F),
    onDanger = Color(0xFFFFFFFF),
    qualityGood = Color(0xFF2E7D32),
    qualityFair = Color(0xFFF9A825),
    qualityPoor = Color(0xFFD84315)
)

private val fallbackTypography = SautiTypography(
    status = TextStyle(fontWeight = FontWeight.Medium),
    timer = TextStyle(fontWeight = FontWeight.SemiBold),
    label = TextStyle(fontWeight = FontWeight.Normal),
    caption = TextStyle(fontWeight = FontWeight.Normal)
)

private val LocalSautiColors = staticCompositionLocalOf { fallbackColors }
private val LocalSautiTypography = staticCompositionLocalOf { fallbackTypography }
private val LocalSautiShapes = staticCompositionLocalOf { defaultShapes() }

object SautiTheme {
    val colors: SautiColors
        @Composable
        @ReadOnlyComposable
        get() = LocalSautiColors.current

    val typography: SautiTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalSautiTypography.current

    val shapes: SautiShapes
        @Composable
        @ReadOnlyComposable
        get() = LocalSautiShapes.current
}

@Composable
fun SautiTheme(
    colors: SautiColors? = null,
    typography: SautiTypography? = null,
    shapes: SautiShapes = defaultShapes(),
    strings: SautiStrings = LocalSautiStrings.current,
    content: @Composable () -> Unit
) {
    val resolvedColors = colors ?: defaultColors()
    val resolvedTypography = typography ?: defaultTypography()
    CompositionLocalProvider(
        LocalSautiColors provides resolvedColors,
        LocalSautiTypography provides resolvedTypography,
        LocalSautiShapes provides shapes,
        LocalSautiStrings provides strings,
        content = content
    )
}
