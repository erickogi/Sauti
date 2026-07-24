package io.sauti.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat

enum class CallPresence {
    CONNECTING,
    ONGOING,
    RECONNECTING
}

object CallNotification {
    const val CHANNEL_ID = "sauti_calls"
    const val NOTIFICATION_ID = 0x5A07

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(io.sauti.android.R.string.sauti_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(io.sauti.android.R.string.sauti_channel_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    fun build(
        context: Context,
        title: String,
        presence: CallPresence,
        fullScreenIntent: PendingIntent,
        hangupIntent: PendingIntent
    ): Notification {
        ensureChannel(context)
        val text = when (presence) {
            CallPresence.CONNECTING -> context.getString(io.sauti.android.R.string.sauti_call_connecting)
            CallPresence.ONGOING -> context.getString(io.sauti.android.R.string.sauti_call_ongoing)
            CallPresence.RECONNECTING -> context.getString(io.sauti.android.R.string.sauti_call_reconnecting)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return buildCallStyle(context, title, text, fullScreenIntent, hangupIntent)
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setFullScreenIntent(fullScreenIntent, true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                context.getString(io.sauti.android.R.string.sauti_call_ongoing),
                hangupIntent
            )
            .build()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun buildCallStyle(
        context: Context,
        title: String,
        text: String,
        fullScreenIntent: PendingIntent,
        hangupIntent: PendingIntent
    ): Notification {
        val person = Person.Builder().setName(title).setImportant(true).build()
        val style = Notification.CallStyle.forOngoingCall(person, hangupIntent)
        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .setContentText(text)
            .setStyle(style)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_CALL)
            .setFullScreenIntent(fullScreenIntent, true)
            .build()
    }

    fun placeholderIntent(context: Context): PendingIntent {
        val intent = Intent()
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            0
        }
        return PendingIntent.getActivity(context, 0, intent, flags)
    }

    fun hangupIntent(context: Context): PendingIntent {
        val intent = Intent(context, CallForegroundService::class.java).apply {
            action = CallForegroundService.ACTION_STOP
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            0
        }
        return PendingIntent.getService(context, 1, intent, flags)
    }
}
