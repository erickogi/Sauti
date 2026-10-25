package io.sauti.ui.compose

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import io.sauti.android.service.CallForegroundService
import io.sauti.ui.compose.outgoing.SautiOutgoingController
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelChildren
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SautiOutgoingStaleAbortGuardTest {

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

    private fun ticket(callId: String) = SautiSessionTicket(
        url = "wss://x",
        token = "t",
        roomId = "r",
        participantId = "p",
        displayTitle = "Call",
        callId = callId
    )

    @Test
    fun staleContinuationDoesNotAbortTheNextOutgoingAttempt() {
        val gate1 = CompletableDeferred<SautiSessionTicket?>()
        val gate2 = CompletableDeferred<SautiSessionTicket?>()
        val gates = ArrayDeque(listOf(gate1, gate2))
        val cancelled = mutableListOf<String>()
        Sauti.configure(
            SautiCallHost.Config(
                onAccept = { null },
                onDecline = {},
                onStartCall = { gates.removeFirst().await() },
                onCancelCall = { cancelled.add(it) },
                outgoingNoAnswerTimeoutMs = 3_600_000L
            )
        )
        val app = shadowOf(context as Application)
        drain(app)

        Sauti.startCall(context, "target-1", mapOf("name" to "Ada"))

        val stopIntent = Intent(context, CallForegroundService::class.java).apply {
            action = CallForegroundService.ACTION_STOP
        }
        val service = Robolectric.buildService(CallForegroundService::class.java, stopIntent).create().get()
        service.onStartCommand(stopIntent, 0, 1)
        drain(app)

        Sauti.startCall(context, "target-2", mapOf("name" to "Bob"))
        drain(app)

        gate1.complete(ticket("call-1"))
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(cancelled.contains("call-1"))
        deliverStartedServices(app)

        assertTrue(SautiOutgoingController.isBusyForTest())
    }

    private fun drain(app: org.robolectric.shadows.ShadowApplication) {
        while (app.nextStartedActivity != null) Unit
        while (app.nextStartedService != null) Unit
    }

    private fun deliverStartedServices(app: org.robolectric.shadows.ShadowApplication) {
        var intent = app.nextStartedService
        while (intent != null) {
            Robolectric.buildService(CallForegroundService::class.java, intent).create().get()
                .onStartCommand(intent, 0, 1)
            intent = app.nextStartedService
        }
    }
}
