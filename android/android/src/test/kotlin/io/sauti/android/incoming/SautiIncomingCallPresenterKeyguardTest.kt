package io.sauti.android.incoming

import android.app.Application
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
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
class SautiIncomingCallPresenterKeyguardTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val target = ComponentName("io.sauti.android.test", "io.sauti.android.test.HostActivity")
    private val call = SautiIncomingCall("kg-call", "kg-room", "Grace")

    @After
    fun tearDown() {
        SautiIncomingCallRegistry.release(call.callId)
    }

    @Test
    fun backgroundUnlockedPostsNotificationAndLaunchesActivity() {
        val presenter = SautiIncomingCallPresenter(
            context = context,
            target = target,
            foreground = ForegroundProbe { false },
            keyguard = KeyguardProbe { false }
        )

        presenter.present(call)

        val started = shadowOf(context as Application).nextStartedActivity
        assertNotNull(started)
        assertEquals(target, started.component)
        val manager = context.getSystemService(NotificationManager::class.java)
        assertEquals(1, shadowOf(manager).size())
    }

    @Test
    fun backgroundLockedPostsNotification() {
        val presenter = SautiIncomingCallPresenter(
            context = context,
            target = target,
            foreground = ForegroundProbe { false },
            keyguard = KeyguardProbe { true }
        )

        presenter.present(call)

        val manager = context.getSystemService(NotificationManager::class.java)
        assertEquals(1, shadowOf(manager).size())
        assertNull(shadowOf(context as Application).nextStartedActivity)
    }

    @Test
    fun postsNotificationWhenUnlockedLaunchThrowsSecurityException() {
        val throwing = object : ContextWrapper(context) {
            override fun startActivity(intent: Intent?) {
                throw SecurityException("blocked")
            }
        }
        val presenter = SautiIncomingCallPresenter(
            context = throwing,
            target = target,
            foreground = ForegroundProbe { false },
            keyguard = KeyguardProbe { false }
        )

        presenter.present(call)

        val manager = context.getSystemService(NotificationManager::class.java)
        assertEquals(1, shadowOf(manager).size())
    }

    @Test
    fun decisionLaunchesWhenForegroundOrUnlocked() {
        val presenter = SautiIncomingCallPresenter(context = context, target = target)
        assertTrue(presenter.shouldLaunchActivity(isForeground = true, isLocked = true))
        assertTrue(presenter.shouldLaunchActivity(isForeground = true, isLocked = false))
        assertTrue(presenter.shouldLaunchActivity(isForeground = false, isLocked = false))
        assertFalse(presenter.shouldLaunchActivity(isForeground = false, isLocked = true))
    }
}
