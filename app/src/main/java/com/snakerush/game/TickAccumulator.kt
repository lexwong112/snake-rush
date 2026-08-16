package com.snakerush.game

/**
 * Converts raw wall-clock time into a fixed number of game ticks while
 * preserving the fractional remainder, so the simulation steps at a constant
 * tick interval regardless of the display frame rate.
 *
 * Pure logic (no Android types) so it can be unit-tested on the JVM.
 * Usage: feed [drain] the elapsed time since the previous frame and the
 * current tick interval; it returns how many [SnakeGame.update] calls to run.
 */
class TickAccumulator {

    private var remainderNanos = 0L

    /**
     * Adds [elapsedNanos] of real time and returns the number of whole ticks
     * of [tickIntervalNanos] that have accumulated. The leftover fraction is
     * kept for the next call.
     */
    fun drain(elapsedNanos: Long, tickIntervalNanos: Long): Int {
        require(tickIntervalNanos > 0) { "tick interval must be positive" }
        remainderNanos += elapsedNanos
        val ticks = (remainderNanos / tickIntervalNanos).toInt()
        remainderNanos %= tickIntervalNanos
        return ticks
    }

    /** Drops any partially accumulated time (e.g. after a pause). */
    fun reset() {
        remainderNanos = 0L
    }
}
