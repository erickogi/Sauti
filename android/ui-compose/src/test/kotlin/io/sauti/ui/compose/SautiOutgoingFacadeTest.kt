package io.sauti.ui.compose

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import io.sauti.android.service.CallForegroundService
import io.sauti.android.service.CallPresence
import kotlinx.coroutines.CompletableDeferred
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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

    @Test
    fun startCallStartsConnectingForegroundServiceAtTapBeforeTicketResolves() {
        Sauti.configure(SautiCallHost.Config(onAccept = { null }, onDecline = {}))

        Sauti.startCall(context, "target-1", mapOf("name" to "Ada"))

        val started = shadowOf(context as Application).nextStartedService
        assertNotNull(started)
        assertEquals(ComponentName(context, CallForegroundService::class.java), started.component)
        assertNull(started.action)
        assertEquals(
            CallPresence.CONNECTING.name,
            started.getStringExtra(CallForegroundService.EXTRA_PRESENCE)
        )
    }

    @Test
    fun startCallThatFailsTicketStopsThePreStartedService() {
        Sauti.configure(SautiCallHost.Config(onAccept = { null }, onDecline = {}, onStartCall = { null }))

        Sauti.startCall(context, "target-1", mapOf("name" to "Ada"))

        val app = shadowOf(context as Application)
        val preStart = app.nextStartedService
        assertNotNull(preStart)
        assertNull(preStart.action)
        assertEquals(
            CallPresence.CONNECTING.name,
            preStart.getStringExtra(CallForegroundService.EXTRA_PRESENCE)
        )
        val stop = app.nextStartedService
        assertNotNull(stop)
        assertEquals(CallForegroundService.ACTION_STOP, stop.action)
    }

    @Test
    fun hangupDuringConnectingWindowAbortsTheOutgoingAttempt() {
        val gate = CompletableDeferred<SautiSessionTicket?>()
        Sauti.configure(
            SautiCallHost.Config(onAccept = { null }, onDecline = {}, onStartCall = { gate.await() })
        )

        Sauti.startCall(context, "target-1", mapOf("name" to "Ada"))
        val app = shadowOf(context as Application)
        assertNotNull(app.nextStartedActivity)

        val stopIntent = Intent(context, CallForegroundService::class.java).apply {
            action = CallForegroundService.ACTION_STOP
        }
        val service = Robolectric.buildService(CallForegroundService::class.java, stopIntent).create().get()
        service.onStartCommand(stopIntent, 0, 1)

        Sauti.startCall(context, "target-2", mapOf("name" to "Bob"))

        val second = app.nextStartedActivity
        assertNotNull(second)
        assertEquals("Bob", second.getStringExtra(SautiCallActivity.EXTRA_OUTGOING_TITLE))
    }

    @Test
    fun outgoingDeclinedDoesNotInvokeCallbackWithoutALiveOutgoing() {
        var fired = 0
        Sauti.configure(
            SautiCallHost.Config(onAccept = { null }, onDecline = {}, onDeclined = { fired += 1 })
        )

        Sauti.outgoingDeclined(context, "call-x")

        assertEquals(0, fired)
    }
}
