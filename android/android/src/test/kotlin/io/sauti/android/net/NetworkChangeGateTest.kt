package io.sauti.android.net

import io.sauti.engine.CallPhase
import kotlin.test.Test
import kotlin.test.assertEquals

class NetworkChangeGateTest {

    private class Clock(var now: Long) {
        fun read(): Long = now
    }

    private fun gate(
        policy: ConnectivityPolicy,
        phase: () -> CallPhase,
        clock: Clock,
        onRestart: () -> Unit
    ): NetworkChangeGate = NetworkChangeGate(
        policy = policy,
        phase = phase,
        onRestart = onRestart,
        clock = clock::read
    )

    @Test
    fun defaultPolicyRestartsOnEveryEventWhenConnected() {
        var restarts = 0
        val clock = Clock(1_000)
        val gate = gate(ConnectivityPolicy(), { CallPhase.CONNECTED }, clock) { restarts += 1 }

        gate.onEvent(NetworkEventKind.Lost)
        clock.now = 1_010
        gate.onEvent(NetworkEventKind.Available)
        clock.now = 1_020
        gate.onEvent(NetworkEventKind.Available)

        assertEquals(3, restarts)
    }

    @Test
    fun preConnectedEventIsIgnored() {
        var restarts = 0
        val clock = Clock(1_000)
        val gate = gate(ConnectivityPolicy(), { CallPhase.CONNECTING }, clock) { restarts += 1 }

        gate.onEvent(NetworkEventKind.Available)

        assertEquals(0, restarts)
    }

    @Test
    fun handoffWithinWindowCoalescesIntoOneRestart() {
        var restarts = 0
        val clock = Clock(50_000)
        val gate = gate(ConnectivityPolicy(debounceMs = 5_000), { CallPhase.CONNECTED }, clock) {
            restarts += 1
        }

        gate.onEvent(NetworkEventKind.Lost)
        clock.now = 50_200
        gate.onEvent(NetworkEventKind.Available)

        assertEquals(1, restarts)
    }

    @Test
    fun eventsPastWindowRestartTwice() {
        var restarts = 0
        val clock = Clock(50_000)
        val gate = gate(ConnectivityPolicy(debounceMs = 5_000), { CallPhase.CONNECTED }, clock) {
            restarts += 1
        }

        gate.onEvent(NetworkEventKind.Lost)
        clock.now = 60_000
        gate.onEvent(NetworkEventKind.Available)

        assertEquals(2, restarts)
    }

    @Test
    fun firstEventUnderDebounceRestarts() {
        var restarts = 0
        val clock = Clock(0)
        val gate = gate(ConnectivityPolicy(debounceMs = 5_000), { CallPhase.CONNECTED }, clock) {
            restarts += 1
        }

        gate.onEvent(NetworkEventKind.Available)

        assertEquals(1, restarts)
    }
}
