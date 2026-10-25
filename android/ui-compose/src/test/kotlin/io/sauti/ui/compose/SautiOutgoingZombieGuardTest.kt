package io.sauti.ui.compose

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import io.sauti.android.service.CallForegroundService
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SautiOutgoingZombieGuardTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun hangupDuringConnectingPostsAdoptedTeardownSoALateAdoptCannotLinger() {
        val gate = CompletableDeferred<SautiSessionTicket?>()
        Sauti.configure(
            SautiCallHost.Config(onAccept = { null }, onDecline = {}, onStartCall = { gate.await() })
        )

        Sauti.startCall(context, "target-1", mapOf("name" to "Ada"))
        val app = shadowOf(context as Application)
        assertNotNull(app.nextStartedActivity)
        val connecting = app.nextStartedService
        assertNotNull(connecting)
        assertNull(connecting.action)

        val stopIntent = Intent(context, CallForegroundService::class.java).apply {
            action = CallForegroundService.ACTION_STOP
        }
        val service = Robolectric.buildService(CallForegroundService::class.java, stopIntent).create().get()
        service.onStartCommand(stopIntent, 0, 1)

        val trailing = app.nextStartedService
        assertNotNull(trailing)
        assertEquals(CallForegroundService.ACTION_STOP_ADOPTED, trailing.action)
    }
}
