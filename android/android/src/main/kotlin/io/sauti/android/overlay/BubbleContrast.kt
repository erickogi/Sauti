package io.sauti.android.overlay

import kotlin.math.pow

enum class ContrastTone { Light, Dark }

const val CONTRAST_PIVOT = 0.179

private fun linearChannel(value: Int): Double {
    val c = value.coerceIn(0, 255) / 255.0
    return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
}

fun relativeLuminance(red: Int, green: Int, blue: Int): Double =
    0.2126 * linearChannel(red) + 0.7152 * linearChannel(green) + 0.0722 * linearChannel(blue)

fun contrastToneOn(red: Int, green: Int, blue: Int): ContrastTone =
    if (relativeLuminance(red, green, blue) < CONTRAST_PIVOT) ContrastTone.Light else ContrastTone.Dark
