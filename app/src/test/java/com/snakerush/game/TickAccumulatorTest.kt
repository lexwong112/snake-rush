package com.snakerush.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * JVM unit tests for the fixed-tick accumulator used by the Choreographer
 * loop in GameView.
 */
class TickAccumulatorTest {

    private val acc = TickAccumulator()

    @Test
    fun zeroElapsedProducesNoTicks() {
        assertEquals(0, acc.drain(0L, 100L))
    }

    @Test
    fun oneWholeIntervalProducesOneTick() {
        assertEquals(1, acc.drain(100L, 100L))
        // remainder is dropped, next frame is clean
        assertEquals(0, acc.drain(0L, 100L))
    }

    @Test
    fun partialTimeCarriesOverBetweenFrames() {
        assertEquals(0, acc.drain(60L, 100L))
        assertEquals(1, acc.drain(40L, 100L)) // 60 + 40 = 100
        assertEquals(0, acc.drain(0L, 100L))
    }

    @Test
    fun longElapsedYieldsMultipleTicksAndKeepsRemainder() {
        assertEquals(3, acc.drain(350L, 100L)) // 3 ticks, 50 left
        assertEquals(0, acc.drain(0L, 100L))
        assertEquals(1, acc.drain(50L, 100L)) // leftover 50 + 50
    }

    @Test
    fun tickingAtNanoGranularity() {
        // 300 ms tick interval in nanos
        val interval = 300L * 1_000_000L
        assertEquals(0, acc.drain(299L * 1_000_000L, interval))
        assertEquals(1, acc.drain(1L * 1_000_000L, interval))
    }

    @Test
    fun resetDropsAccumulatedTime() {
        acc.drain(90L, 100L)
        acc.reset()
        assertEquals(0, acc.drain(0L, 100L))
        assertEquals(1, acc.drain(100L, 100L))
    }

    @Test
    fun drainRejectsNonPositiveInterval() {
        assertThrows(IllegalArgumentException::class.java) { acc.drain(1L, 0L) }
        assertThrows(IllegalArgumentException::class.java) { acc.drain(1L, -5L) }
    }
}
