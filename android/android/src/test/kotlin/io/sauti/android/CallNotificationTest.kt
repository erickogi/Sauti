package io.sauti.android

import android.app.Notification
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.sauti.android.service.CallNotification
import io.sauti.android.service.CallPresence
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
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
}
