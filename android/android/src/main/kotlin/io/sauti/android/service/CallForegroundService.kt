package io.sauti.android.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

class CallForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelfCompat()
                return START_NOT_STICKY
            }
            else -> {
                val title = intent?.getStringExtra(EXTRA_TITLE) ?: DEFAULT_TITLE
                val presence = presenceOf(intent?.getStringExtra(EXTRA_PRESENCE))
                goForeground(title, presence)
            }
        }
        return START_STICKY
    }

    private fun goForeground(title: String, presence: CallPresence) {
        val notification = CallNotification.build(
            context = this,
            title = title,
            presence = presence,
            fullScreenIntent = CallNotification.placeholderIntent(this),
            hangupIntent = CallNotification.hangupIntent(this)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(
                CallNotification.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(CallNotification.NOTIFICATION_ID, notification)
        }
    }

    private fun stopSelfCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun presenceOf(raw: String?): CallPresence = when (raw) {
        CallPresence.RECONNECTING.name -> CallPresence.RECONNECTING
        CallPresence.ONGOING.name -> CallPresence.ONGOING
        else -> CallPresence.CONNECTING
    }

    companion object {
        const val ACTION_STOP = "io.sauti.android.action.STOP"
        const val EXTRA_TITLE = "io.sauti.android.extra.TITLE"
        const val EXTRA_PRESENCE = "io.sauti.android.extra.PRESENCE"
        private const val DEFAULT_TITLE = "Call"

        fun start(context: Context, title: String, presence: CallPresence) {
            val intent = Intent(context, CallForegroundService::class.java).apply {
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_PRESENCE, presence.name)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, CallForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
