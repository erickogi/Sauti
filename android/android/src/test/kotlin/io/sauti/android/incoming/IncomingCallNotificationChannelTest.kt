package io.sauti.android.incoming

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import io.sauti.android.ring.DEFAULT_VIBRATION_PATTERN
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class IncomingCallNotificationChannelTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun channelUsesHighImportancePublicVisibilityAndDefaultVibration() {
        IncomingCallNotification.ensureChannel(context)

        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = manager.getNotificationChannel(IncomingCallNotification.CHANNEL_ID)
        assertNotNull(channel)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
        assertEquals(Notification.VISIBILITY_PUBLIC, channel.lockscreenVisibility)
        assertTrue(channel.shouldVibrate())
        assertTrue(DEFAULT_VIBRATION_PATTERN.contentEquals(channel.vibrationPattern))
    }

    @Test
    fun channelHonoursOverrides() {
        val pattern = longArrayOf(0L, 100L, 200L)
        val ringtone = Uri.parse("content://sauti/ring")
        IncomingCallNotification.ensureChannel(
            context,
            IncomingCallOverrides(ringtoneUri = ringtone, vibrationPattern = pattern)
        )

        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = manager.getNotificationChannel(IncomingCallNotification.CHANNEL_ID)
        assertNotNull(channel)
        assertTrue(pattern.contentEquals(channel.vibrationPattern))
        assertEquals(ringtone, channel.sound)
    }
}
