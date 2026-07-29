package io.sauti.android.net

import io.sauti.engine.CallPhase
import kotlin.test.Test
import kotlin.test.assertEquals

class NetworkChangeReducerTest {

    private fun decide(
        kind: NetworkEventKind,
        phase: CallPhase,
        atMs: Long,
        lastRestartAtMs: Long,
        debounceMs: Long
    ): NetworkDecision = NetworkChangeReducer.reduce(
        NetworkEvent(kind, atMs),
        phase,
        lastRestartAtMs,
        ConnectivityPolicy(debounceMs)
    )

    @Test
    fun defaultPolicyRestartsOnAvailableWhenConnected() {
        val decision = decide(NetworkEventKind.Available, CallPhase.CONNECTED, 1_000, 1_000, 0)
        assertEquals(NetworkDecision.Restart, decision)
    }

    @Test
    fun defaultPolicyRestartsOnLostWhenConnected() {
        val decision = decide(NetworkEventKind.Lost, CallPhase.CONNECTED, 1_000, 1_000, 0)
        assertEquals(NetworkDecision.Restart, decision)
    }

    @Test
    fun connectedIsTheGateForRestart() {
        for (kind in NetworkEventKind.values()) {
            assertEquals(
                NetworkDecision.Restart,
                decide(kind, CallPhase.CONNECTED, 5_000, 0, 0)
            )
        }
    }

    @Test
    fun idleIsIgnoredWithDefaultPolicy() {
        assertEquals(
            NetworkDecision.Ignore,
            decide(NetworkEventKind.Available, CallPhase.IDLE, 1_000, 0, 0)
        )
    }

    @Test
    fun connectingIsIgnored() {
        assertEquals(
            NetworkDecision.Ignore,
            decide(NetworkEventKind.Available, CallPhase.CONNECTING, 1_000, 0, 0)
        )
    }

    @Test
    fun leftIsIgnored() {
        assertEquals(
            NetworkDecision.Ignore,
            decide(NetworkEventKind.Lost, CallPhase.LEFT, 1_000, 0, 0)
        )
    }

    @Test
    fun nonConnectedIsIgnoredEvenUnderDebounce() {
        assertEquals(
            NetworkDecision.Ignore,
            decide(NetworkEventKind.Available, CallPhase.IDLE, 10_000, 0, 5_000)
        )
    }

    @Test
    fun debounceDefersInsideWindow() {
        assertEquals(
            NetworkDecision.Defer,
            decide(NetworkEventKind.Available, CallPhase.CONNECTED, 10_100, 10_000, 5_000)
        )
    }

    @Test
    fun debounceRestartsAtWindowBoundary() {
        assertEquals(
            NetworkDecision.Restart,
            decide(NetworkEventKind.Available, CallPhase.CONNECTED, 15_000, 10_000, 5_000)
        )
    }

    @Test
    fun debounceRestartsPastWindow() {
        assertEquals(
            NetworkDecision.Restart,
            decide(NetworkEventKind.Available, CallPhase.CONNECTED, 20_000, 10_000, 5_000)
        )
    }
}
