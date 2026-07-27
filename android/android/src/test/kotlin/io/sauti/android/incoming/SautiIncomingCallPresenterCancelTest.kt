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
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SautiIncomingCallPresenterCancelTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val target = ComponentName("io.sauti.android.test", "io.sauti.android.test.HostActivity")
    private val call = SautiIncomingCall("cancel-call", "cancel-room", "Ada")

    @After
    fun tearDown() {
        SautiIncomingCallRegistry.finish(call.callId)
        SautiIncomingCallRegistry.release(call.callId)
    }

    @Test
    fun cancelFinishesRegisteredActivityAndCancelsNotification() {
        val presenter = SautiIncomingCallPresenter(
            context = context,
            target = target,
            foreground = ForegroundProbe { false }
        )
        var finished = false
        SautiIncomingCallRegistry.register(call.callId) { finished = true }
        presenter.present(call)

        presenter.cancel(call.callId)

        assertTrue(finished)
        val manager = context.getSystemService(NotificationManager::class.java)
        assertEquals(0, shadowOf(manager).size())
        assertTrue(SautiIncomingCallRegistry.shouldPresent(call.callId))
    }

    @Test
    fun secondPresentForSameCallIdIsDeduped() {
        val presenter = SautiIncomingCallPresenter(
            context = context,
            target = target,
            foreground = ForegroundProbe { false }
        )

        presenter.present(call)
        presenter.present(call)

        val manager = context.getSystemService(NotificationManager::class.java)
        assertEquals(1, shadowOf(manager).size())
    }
}
