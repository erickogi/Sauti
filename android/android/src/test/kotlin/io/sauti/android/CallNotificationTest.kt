package io.sauti.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.sauti.android.service.CallNotification
import io.sauti.android.service.CallPresence
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CallNotificationTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun buildsCallStyleNotification() {
        val notification = CallNotification.build(
            context = context,
            title = "Session",
            presence = CallPresence.ONGOING,
            fullScreenIntent = CallNotification.placeholderIntent(context),
            hangupIntent = CallNotification.hangupIntent(context)
        )
        val template = notification.extras.getString(Notification.EXTRA_TEMPLATE)
        assertTrue(template != null && template.contains("CallStyle"))
        assertTrue(notification.fullScreenIntent != null)
    }

    @Test
    fun ongoingChannelIsLowImportance() {
        CallNotification.build(
            context = context,
            title = "Session",
            presence = CallPresence.ONGOING,
            fullScreenIntent = CallNotification.placeholderIntent(context),
            hangupIntent = CallNotification.hangupIntent(context)
        )
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = manager.getNotificationChannel(CallNotification.CHANNEL_ID)
        assertNotNull(channel)
        assertEquals("sauti_ongoing_call", CallNotification.CHANNEL_ID)
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
    }

    @Test
    fun ensureChannelDeletesLegacyChannel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CallNotification.LEGACY_CHANNEL_ID,
                "Legacy",
                NotificationManager.IMPORTANCE_HIGH
            )
        )

        CallNotification.ensureChannel(context)

        assertNull(manager.getNotificationChannel(CallNotification.LEGACY_CHANNEL_ID))
        assertNotNull(manager.getNotificationChannel(CallNotification.CHANNEL_ID))
    }
}
