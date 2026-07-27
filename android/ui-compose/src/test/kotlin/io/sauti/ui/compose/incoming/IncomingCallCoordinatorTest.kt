package io.sauti.ui.compose.incoming

import kotlin.test.Test
import kotlin.test.assertEquals

private const val CANCEL = "cancel"
private const val RING_START = "ring-start"
private const val RING_STOP = "ring-stop"
private const val RELEASE = "release"

private class RecordingRing(private val log: MutableList<String>) : IncomingRing {
    override fun start() {
        log.add(RING_START)
    }

    override fun stop() {
        log.add(RING_STOP)
    }
}

private fun coordinatorWith(log: MutableList<String>, callId: String = "call-1"): IncomingCallCoordinator =
    IncomingCallCoordinator(
        callId = callId,
        notifications = { log.add(CANCEL) },
        dedupe = { log.add(RELEASE) },
        ring = RecordingRing(log)
    )

class IncomingCallCoordinatorTest {

    @Test
    fun onShownCancelsNotificationBeforeStartingRing() {
        val log = mutableListOf<String>()

        coordinatorWith(log).onShown()

        assertEquals(listOf(CANCEL, RING_START), log)
    }

    @Test
    fun onAcceptedStopsRingWithoutReleasingSlot() {
        val log = mutableListOf<String>()
        val coordinator = coordinatorWith(log)
        coordinator.onShown()
        log.clear()

        coordinator.onAccepted()

        assertEquals(listOf(RING_STOP), log)
    }

    @Test
    fun onDismissedStopsRingCancelsAndReleases() {
        val log = mutableListOf<String>()
        val coordinator = coordinatorWith(log)
        coordinator.onShown()
        log.clear()

        coordinator.onDismissed()

        assertEquals(listOf(RING_STOP, CANCEL, RELEASE), log)
    }

    @Test
    fun onDismissedIsIdempotent() {
        val log = mutableListOf<String>()
        val coordinator = coordinatorWith(log)
        coordinator.onShown()
        log.clear()

        coordinator.onDismissed()
        coordinator.onDismissed()

        assertEquals(listOf(RING_STOP, CANCEL, RELEASE), log)
    }

    @Test
    fun cancelTargetsTheCallId() {
        val cancelled = mutableListOf<String>()
        val coordinator = IncomingCallCoordinator(
            callId = "call-42",
            notifications = { cancelled.add(it) },
            dedupe = {},
            ring = RecordingRing(mutableListOf())
        )

        coordinator.onShown()

        assertEquals(listOf("call-42"), cancelled)
    }
}
