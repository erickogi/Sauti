package io.sauti.android.incoming

import android.app.ActivityManager
import android.content.Context

fun interface ForegroundProbe {
    fun isForeground(): Boolean
}

class DefaultForegroundProbe(private val context: Context) : ForegroundProbe {

    override fun isForeground(): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return false
        val processes = manager.runningAppProcesses ?: return false
        val processName = context.packageName
        return processes.any {
            it.processName == processName &&
                it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        }
    }
}
