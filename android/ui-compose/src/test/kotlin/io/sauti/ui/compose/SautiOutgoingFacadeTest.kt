package io.sauti.ui.compose

import android.app.Application
import android.content.ComponentName
import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
class SautiOutgoingFacadeTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun startCallLaunchesOutgoingHostWithTitleFromMetadataName() {
        Sauti.configure(SautiCallHost.Config(onAccept = { null }, onDecline = {}))

        Sauti.startCall(context, "target-1", mapOf("name" to "Ada"))

        val started = shadowOf(context as Application).nextStartedActivity
        assertNotNull(started)
        assertEquals(ComponentName(context, SautiCallActivity::class.java), started.component)
        assertTrue(started.getBooleanExtra(SautiCallActivity.EXTRA_OUTGOING, false))
        assertEquals("Ada", started.getStringExtra(SautiCallActivity.EXTRA_OUTGOING_TITLE))
    }

    @Test
    fun startCallWithoutNameCarriesAnEmptyTitleButStillOpensTheHost() {
        Sauti.configure(SautiCallHost.Config(onAccept = { null }, onDecline = {}))

        Sauti.startCall(context, "target-2")

        val started = shadowOf(context as Application).nextStartedActivity
        assertNotNull(started)
        assertTrue(started.getBooleanExtra(SautiCallActivity.EXTRA_OUTGOING, false))
        assertEquals("", started.getStringExtra(SautiCallActivity.EXTRA_OUTGOING_TITLE))
    }
}
