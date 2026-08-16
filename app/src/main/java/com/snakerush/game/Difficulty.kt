package com.snakerush.game

/**
 * Play-speed presets. Each difficulty supplies the engine's tick curve:
 * [baseTickMillis] at score 0, shrinking by [speedUpPerScore] per point down
 * to [minTickMillis].
 *
 * The values for [NORMAL] mirror the engine's original fixed formula
 * (`SnakeGame.BASE_TICK_MILLIS` etc.) so a default `SnakeGame()` plays exactly
 * as before — easy is slower, hard is faster.
 */
enum class Difficulty(
    val baseTickMillis: Long,
    val minTickMillis: Long,
    val speedUpPerScore: Long,
) {
    EASY(
        baseTickMillis = 360L,
        minTickMillis = 130L,
        speedUpPerScore = 4L,
    ),
    NORMAL(
        baseTickMillis = SnakeGame.BASE_TICK_MILLIS,
        minTickMillis = SnakeGame.MIN_TICK_MILLIS,
        speedUpPerScore = SnakeGame.SPEEDUP_PER_SCORE,
    ),
    HARD(
        baseTickMillis = 250L,
        minTickMillis = 80L,
        speedUpPerScore = 8L,
    ),
}
