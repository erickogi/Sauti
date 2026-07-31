package io.sauti.android.overlay

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

interface OverlayPermission {
    fun granted(): Boolean
}

class DefaultOverlayPermission(private val context: Context) : OverlayPermission {
    override fun granted(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
}

fun manageOverlayIntent(context: Context): Intent =
    Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:" + context.packageName)
    )
