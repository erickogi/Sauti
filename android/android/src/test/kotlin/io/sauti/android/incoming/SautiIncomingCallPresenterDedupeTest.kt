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
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SautiIncomingCallPresenterDedupeTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val target = ComponentName("io.sauti.android.test", "io.sauti.android.test.HostActivity")
    private val call = SautiIncomingCall("dup-call", "dup-room", "Edsger")

    @After
    fun tearDown() {
        SautiIncomingCallRegistry.release(call.callId)
    }

    @Test
    fun duplicateForegroundPushLaunchesActivityOnce() {
        val presenter = SautiIncomingCallPresenter(
            context = context,
            target = target,
            foreground = ForegroundProbe { true }
        )

        presenter.present(call)
        presenter.present(call)

        val app = shadowOf(context as Application)
        assertNotNull(app.nextStartedActivity)
        assertNull(app.nextStartedActivity)
    }

    @Test
    fun blankCallIdNeitherLaunchesNorNotifies() {
        val presenter = SautiIncomingCallPresenter(
            context = context,
            target = target,
            foreground = ForegroundProbe { false }
        )

        presenter.present(SautiIncomingCall("", "room", "None"))

        val manager = context.getSystemService(NotificationManager::class.java)
        assertEquals(0, shadowOf(manager).size())
        assertNull(shadowOf(context as Application).nextStartedActivity)
    }

    @Test
    fun releaseAllowsRePresentation() {
        val presenter = SautiIncomingCallPresenter(
            context = context,
            target = target,
            foreground = ForegroundProbe { false }
        )

        presenter.present(call)
        presenter.cancel(call.callId)
        presenter.present(call)

        val manager = context.getSystemService(NotificationManager::class.java)
        assertEquals(1, shadowOf(manager).size())
    }
}
