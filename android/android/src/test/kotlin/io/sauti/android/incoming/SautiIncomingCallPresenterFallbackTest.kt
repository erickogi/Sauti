package io.sauti.android.incoming

import android.app.Application
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SautiIncomingCallPresenterFallbackTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val target = ComponentName("io.sauti.android.test", "io.sauti.android.test.MissingActivity")
    private val call = SautiIncomingCall("fallback-call", "fallback-room", "Alan")

    @After
    fun tearDown() {
        SautiIncomingCallRegistry.release(call.callId)
    }

    @Test
    fun postsNotificationWhenForegroundLaunchThrows() {
        shadowOf(context as Application).checkActivities(true)
        val presenter = SautiIncomingCallPresenter(
            context = context,
            target = target,
            foreground = ForegroundProbe { true }
        )

        presenter.present(call)

        val manager = context.getSystemService(NotificationManager::class.java)
        assertEquals(1, shadowOf(manager).size())
    }
}
