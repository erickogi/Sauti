package io.sauti.ui.compose

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import io.sauti.android.service.CallForegroundService
import io.sauti.ui.compose.outgoing.SautiOutgoingController
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelChildren
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import android.os.Looper
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SautiOutgoingZombieGuardTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun resetController() {
        SautiCallHost.scope.coroutineContext.cancelChildren()
        shadowOf(Looper.getMainLooper()).idle()
        SautiOutgoingController.resetForTest()
        CallForegroundService.clearConnectingCanceller()
    }

    @After
    fun clearController() {
        SautiCallHost.scope.coroutineContext.cancelChildren()
        SautiOutgoingController.resetForTest()
        CallForegroundService.clearConnectingCanceller()
    }

    @Test
    fun hangupDuringConnectingPostsAdoptedTeardownSoALateAdoptCannotLinger() {
        val gate = CompletableDeferred<SautiSessionTicket?>()
        Sauti.configure(
            SautiCallHost.Config(
                onAccept = { null },
                onDecline = {},
                onStartCall = { gate.await() },
                outgoingNoAnswerTimeoutMs = 3_600_000L
            )
        )
        val app = shadowOf(context as Application)
        drain(app)

        Sauti.startCall(context, "target-1", mapOf("name" to "Ada"))
        drain(app)

        val stopIntent = Intent(context, CallForegroundService::class.java).apply {
            action = CallForegroundService.ACTION_STOP
        }
        val service = Robolectric.buildService(CallForegroundService::class.java, stopIntent).create().get()
        service.onStartCommand(stopIntent, 0, 1)

        val trailing = app.nextStartedService
        assertNotNull(trailing)
        assertEquals(CallForegroundService.ACTION_STOP_ADOPTED, trailing.action)
    }

    private fun drain(app: org.robolectric.shadows.ShadowApplication) {
        while (app.nextStartedActivity != null) Unit
        while (app.nextStartedService != null) Unit
    }
}
