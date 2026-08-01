package io.sauti.ui.compose

import android.app.Application
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.sauti.android.incoming.ForegroundProbe
import io.sauti.android.incoming.SautiIncomingCall
import io.sauti.android.incoming.SautiIncomingCallRegistry
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
class SautiFacadeTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val call = SautiIncomingCall("facade-call", "facade-room", "Ada", mapOf("name" to "Ada"))

    @After
    fun tearDown() {
        SautiIncomingCallRegistry.finish(call.callId)
        SautiIncomingCallRegistry.release(call.callId)
    }

    @Test
    fun foregroundPresentLaunchesSautiCallActivityWithLibraryKeyedExtras() {
        Sauti.presenter(context, ForegroundProbe { true }).present(call)

        val started = shadowOf(context as Application).nextStartedActivity
        assertNotNull(started)
        assertEquals(ComponentName(context, SautiCallActivity::class.java), started.component)
        assertEquals(call, SautiIncomingCall.fromExtras(started.extras))
    }

    @Test
    fun backgroundPresentPostsFullScreenNotificationTargetingSautiCallActivity() {
        Sauti.presenter(context, ForegroundProbe { false }).present(call)

        val manager = context.getSystemService(NotificationManager::class.java)
        assertEquals(1, shadowOf(manager).size())

        val posted = shadowOf(manager).allNotifications.single()
        val fullScreen = posted.fullScreenIntent
        assertNotNull(fullScreen)
        val routed = shadowOf(fullScreen).savedIntent
        assertEquals(ComponentName(context, SautiCallActivity::class.java), routed.component)
        assertEquals(call, SautiIncomingCall.fromExtras(routed.extras))
    }

    @Test
    fun cancelBeforeRegisterFinishesTheHostFinisherAndClearsNotification() {
        Sauti.presenter(context, ForegroundProbe { false }).present(call)

        Sauti.cancelIncomingCall(context, call.callId)

        var finished = 0
        SautiIncomingCallRegistry.register(call.callId) { finished++ }
        assertEquals(1, finished)

        val manager = context.getSystemService(NotificationManager::class.java)
        assertEquals(0, shadowOf(manager).size())
    }

    @Test
    fun cancelAfterRegisterFinishesTheLiveHostFinisher() {
        var finished = 0
        SautiIncomingCallRegistry.register(call.callId) { finished++ }

        Sauti.cancelIncomingCall(context, call.callId)

        assertTrue(finished == 1)
    }
}
