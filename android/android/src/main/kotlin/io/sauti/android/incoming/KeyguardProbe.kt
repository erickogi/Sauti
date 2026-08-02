package io.sauti.android.incoming

import android.app.KeyguardManager
import android.content.Context

fun interface KeyguardProbe {
    fun isDeviceLocked(): Boolean
}

class DefaultKeyguardProbe(private val context: Context) : KeyguardProbe {

    override fun isDeviceLocked(): Boolean {
        val manager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            ?: return false
        return manager.isKeyguardLocked
    }
}
