package io.sauti.android.incoming

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.sauti.android.ring.DEFAULT_VIBRATION_PATTERN

data class IncomingCallOverrides(
    val ringtoneUri: Uri? = null,
    val vibrationPattern: LongArray = DEFAULT_VIBRATION_PATTERN
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IncomingCallOverrides) return false
        return ringtoneUri == other.ringtoneUri &&
            vibrationPattern.contentEquals(other.vibrationPattern)
    }

    override fun hashCode(): Int {
        var result = ringtoneUri?.hashCode() ?: 0
        result = 31 * result + vibrationPattern.contentHashCode()
        return result
    }
}

data class CallActionIntents(
    val answer: PendingIntent,
    val decline: PendingIntent
)

object IncomingCallNotification {
    const val CHANNEL_ID = "sauti_incoming_calls"

    fun ensureChannel(context: Context, overrides: IncomingCallOverrides = IncomingCallOverrides()) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val ringtone = overrides.ringtoneUri ?: defaultRingtone(context)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(io.sauti.android.R.string.sauti_incoming_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(io.sauti.android.R.string.sauti_incoming_channel_description)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(ringtone, audioAttributes)
            enableVibration(true)
            vibrationPattern = overrides.vibrationPattern
        }
        manager.createNotificationChannel(channel)
    }

    fun build(
        context: Context,
        call: SautiIncomingCall,
        target: ComponentName,
        overrides: IncomingCallOverrides = IncomingCallOverrides(),
        actions: CallActionIntents? = null
    ): Notification {
        ensureChannel(context, overrides)
        val pendingIntent = activityPendingIntent(context, call, target)
        val title = call.callerName?.takeIf { it.isNotBlank() }
            ?: context.getString(io.sauti.android.R.string.sauti_incoming_call_title)

        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && actions != null) {
            buildCallStyle(context, title, pendingIntent, actions)
        } else {
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_phone_call)
                .setContentTitle(title)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOngoing(true)
                .setAutoCancel(true)
                .setFullScreenIntent(pendingIntent, true)
                .setContentIntent(pendingIntent)
                .build()
        }
        notification.flags = notification.flags or Notification.FLAG_INSISTENT
        return notification
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun buildCallStyle(
        context: Context,
        title: String,
        pendingIntent: PendingIntent,
        actions: CallActionIntents
    ): Notification {
        val person = Person.Builder().setName(title).setImportant(true).build()
        val style = Notification.CallStyle.forIncomingCall(person, actions.decline, actions.answer)
        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .setStyle(style)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_CALL)
            .setFullScreenIntent(pendingIntent, true)
            .setContentIntent(pendingIntent)
            .build()
    }

    fun post(
        context: Context,
        call: SautiIncomingCall,
        target: ComponentName,
        overrides: IncomingCallOverrides = IncomingCallOverrides(),
        actions: CallActionIntents? = null
    ) {
        val notification = build(context, call, target, overrides, actions)
        NotificationManagerCompat.from(context).notify(notificationId(call.callId), notification)
    }

    fun cancel(context: Context, callId: String) {
        NotificationManagerCompat.from(context).cancel(notificationId(callId))
    }

    fun notificationId(callId: String): Int = callId.hashCode()

    private fun activityPendingIntent(
        context: Context,
        call: SautiIncomingCall,
        target: ComponentName
    ): PendingIntent {
        val intent = call.toExtras(Intent().setComponent(target))
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getActivity(context, notificationId(call.callId), intent, flags)
    }

    private fun defaultRingtone(context: Context): Uri? =
        RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
}
