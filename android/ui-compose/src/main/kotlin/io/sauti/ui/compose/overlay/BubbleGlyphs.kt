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

private const val SPEAKER_PATH =
    "M3,9v6h4l5,5V4L7,9H3zm13.5,3c0,-1.77 -1.02,-3.29 -2.5,-4.03v8.05c1.48,-0.73 2.5,-2.25 " +
        "2.5,-4.02zM14,3.23v2.06c2.89,0.86 5,3.54 5,6.71s-2.11,5.85 -5,6.71v2.06c4.01,-0.91 " +
        "7,-4.49 7,-8.77s-2.99,-7.86 -7,-8.77z"

private const val EARPIECE_PATH =
    "M15,12h2c0,-2.76 -2.24,-5 -5,-5v2c1.66,0 3,1.34 3,3zm4,0h2c0,-4.97 -4.03,-9 -9,-9v2c3.87,0 " +
        "7,3.13 7,7zm1,3.5c-1.25,0 -2.45,-0.2 -3.57,-0.57 -0.35,-0.11 -0.74,-0.03 -1.02,0.24l-2.2,2.2c" +
        "-2.83,-1.44 -5.15,-3.75 -6.59,-6.59l2.2,-2.21c0.28,-0.26 0.36,-0.65 0.25,-1C8.7,9.45 8.5,8.25 " +
        "8.5,7c0,-0.55 -0.45,-1 -1,-1H4c-0.55,0 -1,0.45 -1,1 0,9.39 7.61,17 17,17 0.55,0 1,-0.45 " +
        "1,-1v-3.5c0,-0.55 -0.45,-1 -1,-1z"

private const val BLUETOOTH_PATH =
    "M17.71,7.71L12,2h-1v7.59L6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 11,14.41V22h1l5.71,-5.71 " +
        "-4.3,-4.29 4.3,-4.29zM13,5.83l1.88,1.88L13,9.59V5.83zm1.88,10.46L13,18.17v-3.76l1.88,1.88z"

private const val HEADSET_PATH =
    "M12,1c-4.97,0 -9,4.03 -9,9v7c0,1.66 1.34,3 3,3h3v-8L5,12v-2c0,-3.87 3.13,-7 7,-7s7,3.13 7,7v2h-4v8h3c" +
        "1.66,0 3,-1.34 3,-3v-7c0,-4.97 -4.03,-9 -9,-9z"

private const val MINIMIZE_PATH =
    "M7,10l5,5 5,-5z"

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

@Composable
fun SpeakerGlyph(color: Color, modifier: Modifier = Modifier) =
    PathGlyph(SPEAKER_PATH, color, modifier)

@Composable
fun EarpieceGlyph(color: Color, modifier: Modifier = Modifier) =
    PathGlyph(EARPIECE_PATH, color, modifier)

@Composable
fun BluetoothGlyph(color: Color, modifier: Modifier = Modifier) =
    PathGlyph(BLUETOOTH_PATH, color, modifier)

@Composable
fun HeadsetGlyph(color: Color, modifier: Modifier = Modifier) =
    PathGlyph(HEADSET_PATH, color, modifier)

@Composable
fun MinimizeGlyph(color: Color, modifier: Modifier = Modifier) =
    PathGlyph(MINIMIZE_PATH, color, modifier)
