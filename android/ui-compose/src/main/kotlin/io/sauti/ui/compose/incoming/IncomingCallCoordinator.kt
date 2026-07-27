package io.sauti.ui.compose.incoming

internal interface IncomingRing {
    fun start()
    fun stop()
}

internal fun interface IncomingNotifications {
    fun cancel(callId: String)
}

internal fun interface IncomingDedupe {
    fun release(callId: String)
}

internal class IncomingCallCoordinator(
    private val callId: String,
    private val notifications: IncomingNotifications,
    private val dedupe: IncomingDedupe,
    private val ring: IncomingRing
) {

    private var dismissed = false

    fun onShown() {
        notifications.cancel(callId)
        ring.start()
    }

    fun onAccepted() {
        ring.stop()
    }

    fun onDismissed() {
        if (dismissed) return
        dismissed = true
        ring.stop()
        notifications.cancel(callId)
        dedupe.release(callId)
    }
}
