package io.sauti.android.incoming

import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class IncomingCallNotificationCallStyleTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val target = ComponentName("io.sauti.android.test", "io.sauti.android.test.HostActivity")

    @Test
    fun callStyleNotificationCarriesFullScreenIntentAndCategory() {
        val call = SautiIncomingCall("style-call", "style-room", "Grace")

        val notification = IncomingCallNotification.build(
            context = context,
            call = call,
            target = target,
            actions = CallActionIntents(
                answer = pendingIntent(1),
                decline = pendingIntent(2)
            )
        )

        assertNotNull(notification.fullScreenIntent)
        assertEquals(Notification.CATEGORY_CALL, notification.category)
        assertEquals(
            Notification.CallStyle::class.java.name,
            notification.extras.getString(Notification.EXTRA_TEMPLATE)
        )
    }

    private fun pendingIntent(request: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            request,
            Intent(),
            PendingIntent.FLAG_IMMUTABLE
        )
}
