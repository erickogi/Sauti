package io.sauti.android.net

import io.sauti.engine.CallPhase

class NetworkChangeGate(
    private val policy: ConnectivityPolicy,
    private val phase: () -> CallPhase,
    private val onRestart: () -> Unit,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private var lastRestartAtMs = NEVER

    fun onEvent(kind: NetworkEventKind) {
        val event = NetworkEvent(kind, clock())
        val decision = NetworkChangeReducer.reduce(event, phase(), lastRestartAtMs, policy)
        if (decision == NetworkDecision.Restart) {
            lastRestartAtMs = event.atMs
            onRestart()
        }
    }

    private companion object {
        const val NEVER = Long.MIN_VALUE / 2
    }
}
