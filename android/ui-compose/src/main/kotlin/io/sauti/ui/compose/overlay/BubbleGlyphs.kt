package io.sauti.ui.compose.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser

private const val VIEWPORT = 24f

private const val PHONE_PATH =
    "M6.62,10.79c1.44,2.83 3.76,5.14 6.59,6.59l2.2,-2.2c0.27,-0.27 0.67,-0.36 1.02,-0.24 " +
        "1.12,0.37 2.33,0.57 3.57,0.57 0.55,0 1,0.45 1,1V20c0,0.55 -0.45,1 -1,1 -9.39,0 " +
        "-17,-7.61 -17,-17 0,-0.55 0.45,-1 1,-1h3.5c0.55,0 1,0.45 1,1 0,1.25 0.2,2.45 0.57,3.57 " +
        "0.11,0.35 0.03,0.74 -0.25,1.02l-2.2,2.2z"

private const val MIC_PATH =
    "M12,14c1.66,0 2.99,-1.34 2.99,-3L15,5c0,-1.66 -1.34,-3 -3,-3S9,3.34 9,5v6c0,1.66 1.34,3 3,3z" +
        "M17.3,11c0,3 -2.54,5.1 -5.3,5.1S6.7,14 6.7,11L5,11c0,3.41 2.72,6.23 6,6.72V21h2v-3.28c3.28," +
        "-0.48 6,-3.3 6,-6.72h-1.7z"

private const val MIC_OFF_PATH =
    "M19,11h-1.7c0,0.74 -0.16,1.43 -0.43,2.05l1.23,1.23c0.56,-0.98 0.9,-2.09 0.9,-3.28z" +
        "M14.98,11.17c0,-0.06 0.02,-0.11 0.02,-0.17L15,5c0,-1.66 -1.34,-3 -3,-3 -1.54,0 -2.79,1.16 " +
        "-2.96,2.65l5.94,5.52zM4.27,3L3,4.27l6.01,6.01L9.01,11c0,1.66 1.33,3 2.99,3 0.22,0 0.44,-0.03 " +
        "0.65,-0.08l1.66,1.66c-0.71,0.33 -1.5,0.52 -2.31,0.52 -2.76,0 -5.3,-2.1 -5.3,-5.1L5,11c0,3.41 " +
        "2.72,6.23 6,6.72V21h2v-3.28c0.91,-0.13 1.77,-0.45 2.54,-0.9L19.73,21 21,19.73 4.27,3z"

private const val CALL_END_PATH =
    "M12,9c-1.6,0 -3.15,0.25 -4.6,0.72v3.1c0,0.39 -0.23,0.74 -0.56,0.9 -0.98,0.49 -1.87,1.12 " +
        "-2.66,1.85 -0.18,0.18 -0.43,0.28 -0.7,0.28 -0.28,0 -0.53,-0.11 -0.71,-0.29L0.29,13.08c-0.18," +
        "-0.17 -0.29,-0.42 -0.29,-0.7 0,-0.28 0.11,-0.53 0.29,-0.71C3.34,8.78 7.46,7 12,7s8.66,1.78 " +
        "11.71,4.67c0.18,0.18 0.29,0.43 0.29,0.71 0,0.28 -0.11,0.53 -0.29,0.71l-2.48,2.48c-0.18,0.18 " +
        "-0.43,0.29 -0.71,0.29 -0.27,0 -0.52,-0.11 -0.7,-0.28 -0.79,-0.74 -1.69,-1.36 -2.67,-1.85 " +
        "-0.33,-0.16 -0.56,-0.5 -0.56,-0.9v-3.1C15.15,9.25 13.6,9 12,9z"

private const val RETURN_CHEVRON_PATH =
    "M15.41,7.41L14,6l-6,6 6,6 1.41,-1.41L10.83,12z"

@Composable
private fun PathGlyph(pathData: String, color: Color, modifier: Modifier) {
    val path = remember(pathData) { PathParser().parsePathString(pathData).toPath() }
    Canvas(modifier = modifier) {
        val factor = size.minDimension / VIEWPORT
        scale(factor, factor, pivot = Offset.Zero) {
            drawPath(path, color)
        }
    }
}

@Composable
fun PhoneGlyph(color: Color, modifier: Modifier = Modifier) = PathGlyph(PHONE_PATH, color, modifier)

@Composable
fun MicGlyph(color: Color, modifier: Modifier = Modifier) = PathGlyph(MIC_PATH, color, modifier)

@Composable
fun MicOffGlyph(color: Color, modifier: Modifier = Modifier) = PathGlyph(MIC_OFF_PATH, color, modifier)

@Composable
fun CallEndGlyph(color: Color, modifier: Modifier = Modifier) = PathGlyph(CALL_END_PATH, color, modifier)

@Composable
fun ReturnChevronGlyph(color: Color, modifier: Modifier = Modifier) =
    PathGlyph(RETURN_CHEVRON_PATH, color, modifier)
