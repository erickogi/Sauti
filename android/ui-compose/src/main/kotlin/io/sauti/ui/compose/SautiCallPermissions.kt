package io.sauti.ui.compose

import android.Manifest
import android.os.Build

object SautiCallPermissions {

    fun required(sdkInt: Int, base: List<String>): List<String> {
        val result = base.toMutableList()
        if (sdkInt >= Build.VERSION_CODES.TIRAMISU &&
            Manifest.permission.POST_NOTIFICATIONS !in result
        ) {
            result.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return result
    }

    fun missing(required: List<String>, granted: Set<String>): List<String> =
        required.filter { it !in granted }
}
