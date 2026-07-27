package io.sauti.android.incoming

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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class IncomingCallNotificationCancelTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val target = ComponentName("io.sauti.android.test", "io.sauti.android.test.HostActivity")

    @After
    fun tearDown() {
        SautiIncomingCallRegistry.release("keep")
        SautiIncomingCallRegistry.release("drop")
    }

    @Test
    fun cancelRemovesOnlyThatNotification() {
        val keep = SautiIncomingCall("keep", "room-keep", "Keep")
        val drop = SautiIncomingCall("drop", "room-drop", "Drop")
        IncomingCallNotification.post(context, keep, target)
        IncomingCallNotification.post(context, drop, target)

        IncomingCallNotification.cancel(context, drop.callId)

        val manager = context.getSystemService(NotificationManager::class.java)
        val shadow = shadowOf(manager)
        assertEquals(1, shadow.size())
        assertNotNull(shadow.getNotification(IncomingCallNotification.notificationId(keep.callId)))
        assertNull(shadow.getNotification(IncomingCallNotification.notificationId(drop.callId)))
    }

    @Test
    fun presenterCancelReleasesRegistrySlot() {
        val presenter = SautiIncomingCallPresenter(
            context = context,
            target = target,
            foreground = ForegroundProbe { false }
        )
        val call = SautiIncomingCall("drop", "room-drop", "Drop")

        presenter.present(call)
        assertFalse(SautiIncomingCallRegistry.shouldPresent(call.callId))

        presenter.cancel(call.callId)

        assertTrue(SautiIncomingCallRegistry.shouldPresent(call.callId))
        val manager = context.getSystemService(NotificationManager::class.java)
        assertEquals(0, shadowOf(manager).size())
    }
}
