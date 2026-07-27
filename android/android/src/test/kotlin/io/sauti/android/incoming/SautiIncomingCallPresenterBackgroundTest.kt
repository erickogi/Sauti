package io.sauti.android.incoming

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SautiIncomingCallPresenterBackgroundTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val target = ComponentName("io.sauti.android.test", "io.sauti.android.test.HostActivity")
    private val call = SautiIncomingCall("bg-call", "bg-room", "Ada")

    @After
    fun tearDown() {
        SautiIncomingCallRegistry.release(call.callId)
    }

    @Test
    fun postsFullScreenCallNotificationAndNoActivity() {
        val presenter = SautiIncomingCallPresenter(
            context = context,
            target = target,
            foreground = ForegroundProbe { false }
        )

        presenter.present(call)

        val manager = context.getSystemService(NotificationManager::class.java)
        val shadow = shadowOf(manager)
        assertEquals(1, shadow.size())
        val posted = shadow.getNotification(IncomingCallNotification.notificationId(call.callId))
        assertNotNull(posted)
        assertEquals(Notification.CATEGORY_CALL, posted.category)
        assertNotNull(posted.fullScreenIntent)
        assertTrue(posted.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue(posted.flags and Notification.FLAG_INSISTENT != 0)
        assertEquals(call.callerName, posted.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(0, shadowOf(context as android.app.Application).nextStartedActivity?.let { 1 } ?: 0)
    }

    @Test
    fun channelIsHighImportanceForBackgroundPost() {
        val presenter = SautiIncomingCallPresenter(
            context = context,
            target = target,
            foreground = ForegroundProbe { false }
        )

        presenter.present(call)

        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = manager.getNotificationChannel(IncomingCallNotification.CHANNEL_ID)
        assertNotNull(channel)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
    }
}
