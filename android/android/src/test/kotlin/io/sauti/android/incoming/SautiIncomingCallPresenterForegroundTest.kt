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
import kotlin.test.assertNotNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SautiIncomingCallPresenterForegroundTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val target = ComponentName("io.sauti.android.test", "io.sauti.android.test.HostActivity")
    private val call = SautiIncomingCall("fg-call", "fg-room", "Grace", mapOf("name" to "Grace"))

    @After
    fun tearDown() {
        SautiIncomingCallRegistry.release(call.callId)
    }

    @Test
    fun launchesActivityWithMatchingComponentAndExtrasAndPostsNoNotification() {
        val presenter = SautiIncomingCallPresenter(
            context = context,
            target = target,
            foreground = ForegroundProbe { true }
        )

        presenter.present(call)

        val started = shadowOf(context as android.app.Application).nextStartedActivity
        assertNotNull(started)
        assertEquals(target, started.component)
        assertEquals(call, SautiIncomingCall.fromExtras(started.extras))

        val manager = context.getSystemService(NotificationManager::class.java)
        assertEquals(0, shadowOf(manager).size())
    }
}
