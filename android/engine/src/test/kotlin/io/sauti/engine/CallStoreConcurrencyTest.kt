package io.sauti.engine

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CyclicBarrier
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertTrue

class CallStoreConcurrencyTest {

    private fun seed(id: String) = ParticipantSeed(
        participantId = id,
        metadata = emptyMap(),
        muted = false,
        onHold = false,
        connectionState = ConnectionState.CONNECTED
    )

    @Test
    fun mutationFromOneThreadWhileAnotherBuildsStateThrowsNothing() {
        val store = CallStore { 0L }
        store.setSelf("self")
        val failures = CopyOnWriteArrayList<Throwable>()
        val rounds = 5_000
        val barrier = CyclicBarrier(3)

        val writer = thread {
            barrier.await()
            runCatching {
                repeat(rounds) { i ->
                    store.upsertParticipant(seed("peer-${i % 6}"))
                    store.removeParticipant("peer-${i % 6}")
                }
            }.onFailure { failures += it }
        }
        val ticker = thread {
            barrier.await()
            runCatching { repeat(rounds) { store.tick() } }.onFailure { failures += it }
        }
        val reader = thread {
            barrier.await()
            runCatching {
                repeat(rounds) {
                    store.state.value.participants
                    store.participant("peer-1")
                }
            }.onFailure { failures += it }
        }

        writer.join()
        ticker.join()
        reader.join()

        assertTrue(failures.isEmpty(), "concurrent store access threw: ${failures.joinToString { it.toString() }}")
    }
}
