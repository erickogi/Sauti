package io.sauti.engine

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CyclicBarrier
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QoeAggregatorTest {

    @Test
    fun accumulatesAverageMaxBandSecondsAndWorst() {
        val agg = QoeAggregator(2.0)
        agg.observe(Quality.GOOD, 20.0, 0.0, 5.0)
        agg.observe(Quality.DEGRADED, 200.0, 0.04, 40.0)
        agg.observe(Quality.POOR, 400.0, 0.1, 80.0)

        val s = agg.summary()!!
        assertEquals(3, s.samples)
        assertEquals(206.7, s.avgRttMs)
        assertEquals(400.0, s.maxRttMs)
        assertEquals(80.0, s.maxJitterMs)
        assertEquals(0.1, s.maxLoss)
        assertEquals(2.0, s.secGood)
        assertEquals(2.0, s.secDegraded)
        assertEquals(2.0, s.secPoor)
        assertEquals(Quality.POOR, s.worst)
    }

    @Test
    fun emptyAggregatorHasNoSummary() {
        assertNull(QoeAggregator(2.0).summary())
    }

    @Test
    fun concurrentObserveAndSummaryLoseNoUpdatesAndThrowNothing() {
        val agg = QoeAggregator(2.0)
        val failures = CopyOnWriteArrayList<Throwable>()
        val rounds = 5_000
        val barrier = CyclicBarrier(3)

        val writers = (0 until 2).map {
            thread {
                barrier.await()
                runCatching { repeat(rounds) { agg.observe(Quality.DEGRADED, 100.0, 0.01, 10.0) } }
                    .onFailure { failures += it }
            }
        }
        val reader = thread {
            barrier.await()
            runCatching { repeat(rounds) { agg.summary() } }.onFailure { failures += it }
        }

        writers.forEach { it.join() }
        reader.join()

        assertTrue(failures.isEmpty(), "concurrent aggregator access threw: $failures")
        assertEquals(2 * rounds, agg.summary()!!.samples)
    }

    @Test
    fun averagesRttOnlyOverNonzeroSamplesAndIgnoresNaNLoss() {
        val agg = QoeAggregator(2.0)
        agg.observe(Quality.GOOD, 0.0, Double.NaN, 5.0)
        agg.observe(Quality.GOOD, 100.0, 0.0, 5.0)

        val s = agg.summary()!!
        assertEquals(100.0, s.avgRttMs)
        assertEquals(0.0, s.avgLoss)
    }
}
