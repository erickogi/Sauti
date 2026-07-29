package io.sauti.android.net

import io.sauti.engine.CallPhase

enum class NetworkEventKind {
    Available,
    Lost
}

data class NetworkEvent(
    val kind: NetworkEventKind,
    val atMs: Long
)

data class ConnectivityPolicy(
    val debounceMs: Long = 0
)

enum class NetworkDecision {
    Restart,
    Ignore,
    Defer
}

object NetworkChangeReducer {
    fun reduce(
        event: NetworkEvent,
        phase: CallPhase,
        lastRestartAtMs: Long,
        policy: ConnectivityPolicy
    ): NetworkDecision = when {
        phase != CallPhase.CONNECTED -> NetworkDecision.Ignore
        policy.debounceMs <= 0L -> NetworkDecision.Restart
        event.atMs - lastRestartAtMs >= policy.debounceMs -> NetworkDecision.Restart
        else -> NetworkDecision.Defer
    }
}
