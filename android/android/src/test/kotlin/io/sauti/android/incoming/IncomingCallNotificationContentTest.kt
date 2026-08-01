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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class IncomingCallNotificationContentTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val target = ComponentName("io.sauti.android.test", "io.sauti.android.test.HostActivity")

    @Test
    fun plainPathAppliesOverriddenSmallIcon() {
        val notification = IncomingCallNotification.build(
            context = context,
            call = SautiIncomingCall("plain-icon", "room"),
            target = target,
            overrides = IncomingCallOverrides(smallIcon = android.R.drawable.ic_menu_call)
        )

        assertEquals(android.R.drawable.ic_menu_call, notification.smallIcon.resId)
    }

    @Test
    fun callStylePathAppliesOverriddenSmallIcon() {
        val notification = IncomingCallNotification.build(
            context = context,
            call = SautiIncomingCall("style-icon", "room", "Grace"),
            target = target,
            overrides = IncomingCallOverrides(smallIcon = android.R.drawable.ic_menu_call),
            actions = CallActionIntents(answer = pendingIntent(1), decline = pendingIntent(2))
        )

        assertEquals(android.R.drawable.ic_menu_call, notification.smallIcon.resId)
    }

    @Test
    fun contentTextIsAppliedWhenSupplied() {
        val notification = IncomingCallNotification.build(
            context = context,
            call = SautiIncomingCall("body", "room"),
            target = target,
            overrides = IncomingCallOverrides(contentText = "Tap to answer")
        )

        assertEquals("Tap to answer", notification.extras.getString(Notification.EXTRA_TEXT))
    }

    @Test
    fun defaultsUnchangedWhenOverridesNull() {
        val notification = IncomingCallNotification.build(
            context = context,
            call = SautiIncomingCall("default", "room"),
            target = target
        )

        assertEquals(android.R.drawable.stat_sys_phone_call, notification.smallIcon.resId)
        assertEquals(null, notification.extras.getString(Notification.EXTRA_TEXT))
    }

    private fun pendingIntent(request: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            request,
            Intent(),
            PendingIntent.FLAG_IMMUTABLE
        )
}
