package io.sauti.android.telephony

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import io.sauti.android.Startable
import java.util.concurrent.Executor

class TelephonyWatcher(
    context: Context,
    private val onCellularActive: (Boolean) -> Unit
) : Startable {
    private val appContext = context.applicationContext
    private val telephonyManager =
        appContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    private var modernCallback: Any? = null
    private var legacyListener: PhoneStateListener? = null

    override fun start() {
        if (!hasPermission()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            startModern()
        } else {
            startLegacy()
        }
    }

    override fun stop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (modernCallback as? TelephonyCallback)?.let { telephonyManager.unregisterTelephonyCallback(it) }
            modernCallback = null
        } else {
            legacyListener?.let {
                @Suppress("DEPRECATION")
                telephonyManager.listen(it, PhoneStateListener.LISTEN_NONE)
            }
            legacyListener = null
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun startModern() {
        val executor: Executor = ContextCompat.getMainExecutor(appContext)
        val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
            override fun onCallStateChanged(state: Int) {
                dispatch(state)
            }
        }
        modernCallback = callback
        telephonyManager.registerTelephonyCallback(executor, callback)
    }

    private fun startLegacy() {
        @Suppress("DEPRECATION")
        val listener = object : PhoneStateListener() {
            @Deprecated("Deprecated in Java")
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                dispatch(state)
            }
        }
        legacyListener = listener
        @Suppress("DEPRECATION")
        telephonyManager.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
    }

    private fun dispatch(state: Int) {
        val active = state == TelephonyManager.CALL_STATE_RINGING ||
            state == TelephonyManager.CALL_STATE_OFFHOOK
        onCellularActive(active)
    }

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, android.Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED
}
