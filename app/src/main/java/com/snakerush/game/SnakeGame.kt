package com.snakerush.game

import kotlin.random.Random

/**
 * Pure-Kotlin Snake game engine.
 *
 * Owns all gameplay state and rules; knows nothing about Android or rendering,
 * so it can be unit-tested on a plain JVM. The UI layer drives it by calling
 * [setDirection] and [update] on a fixed tick schedule.
 *
 * Coordinate system: (0,0) is the top-left cell, x grows right, y grows down.
 * The snake is stored head-first in an [ArrayDeque].
 */
class SnakeGame(
    val cols: Int = DEFAULT_COLS,
    val rows: Int = DEFAULT_ROWS,
    /** Play-speed preset; controls the tick interval curve (default: [Difficulty.NORMAL]). */
    val difficulty: Difficulty = Difficulty.NORMAL,
) {

    /** Snake cells, head first. Read-only from the outside. */
    val snake: ArrayDeque<GridPoint> = ArrayDeque()

    /** Direction the snake is currently travelling. */
    var direction: Direction = Direction.RIGHT
        private set

    /**
     * Pending directions, one is consumed per [update] tick. A small buffer
     * is needed so a player who swipes twice within a single tick (e.g. DOWN
     * then LEFT) gets both turns applied in order — keeping only the latest
     * input would turn the snake straight back into itself. Reversal checks
     * are done against the *last pending* direction, not the current one.
     */
    private val pending = ArrayDeque<Direction>()

    /** Cell containing the food the snake must eat. */
    var food: GridPoint = GridPoint(0, 0)
        private set

    var score: Int = 0
        private set

    /** Total number of ticks executed since the last [reset]. */
    var tickCount: Long = 0
        private set

    var state: GameState = GameState.MENU
        private set

    /** Human-readable reason the game ended; null while not in [GameState.GAME_OVER]. */
    var gameOverReason: String? = null
        private set

    private val random = Random.Default

    val head: GridPoint
        get() = snake.first()

    /**
     * Tick interval in ms, derived from [difficulty]. Speeds up (gets shorter)
     * as the score grows, floored at the difficulty's [Difficulty.minTickMillis].
     */
    val tickIntervalMillis: Long
        get() = (difficulty.baseTickMillis - score * difficulty.speedUpPerScore)
            .coerceAtLeast(difficulty.minTickMillis)

    init {
        reset()
    }

    /** Restores the initial layout and returns to [GameState.MENU]. */
    fun reset() {
        snake.clear()
        val startX = cols / 2
        val startY = rows / 2
        repeat(INITIAL_LENGTH) { i ->
            snake.addLast(GridPoint(startX - i, startY))
        }
        direction = Direction.RIGHT
        pending.clear()
        score = 0
        tickCount = 0
        gameOverReason = null
        spawnFood()
        state = GameState.MENU
    }

    /** Begins play from [GameState.MENU]. */
    fun start() {
        if (state == GameState.MENU) state = GameState.PLAYING
    }

    fun pause() {
        if (state == GameState.PLAYING) state = GameState.PAUSED
    }

    fun resume() {
        if (state == GameState.PAUSED) state = GameState.PLAYING
    }

    /**
     * Queues the player's desired direction for the next tick(s).
     * 180° reversals are silently ignored (checked against the last queued
     * direction, or the current one if nothing is queued).
     */
    fun setDirection(dir: Direction) {
        val reference = pending.lastOrNull() ?: direction
        if (dir.isOpposite(reference)) return
        if (pending.size >= MAX_QUEUED_INPUTS) pending.removeFirst()
        pending.addLast(dir)
    }

    /**
     * Advances the simulation by one tick.
     *
     * @return true if a tick was simulated, false if the game is not
     *         in [GameState.PLAYING] (nothing changed).
     */
    fun update(): Boolean {
        if (state != GameState.PLAYING) return false
        if (pending.isNotEmpty()) direction = pending.removeFirst()
        val newHead = head.translated(direction)
        val willEat = newHead == food
        tickCount++

        if (newHead.x !in 0 until cols || newHead.y !in 0 until rows) {
            gameOver(WALL_HIT_MESSAGE)
            return true
        }

        // Collision with the body. The tail cell is about to move away, so it
        // only counts as a collision when we grow (willEat keeps the tail).
        val body = if (willEat) snake else snake.dropLast(1)
        if (newHead in body) {
            gameOver(SELF_HIT_MESSAGE)
            return true
        }

        snake.addFirst(newHead)
        if (willEat) {
            score += FOOD_POINTS
            spawnFood()
        } else {
            snake.removeLast()
        }
        return true
    }

    /** True if [point] is currently occupied by the snake. */
    fun occupies(point: GridPoint): Boolean = point in snake

    /**
     * Places the food at a specific free cell. Internal — used by unit tests
     * to build deterministic scenarios (also usable for future level design).
     */
    internal fun placeFoodAt(point: GridPoint) {
        require(point.x in 0 until cols && point.y in 0 until rows) { "food must be inside the board" }
        require(point !in snake) { "food cannot spawn on the snake" }
        food = point
    }

    /**
     * Replaces the snake body and current direction with the given state.
     * Internal — used by unit tests to build deterministic collision scenarios.
     */
    internal fun loadStateForTesting(cells: List<GridPoint>, dir: Direction) {
        require(cells.isNotEmpty()) { "snake needs at least one cell" }
        snake.clear()
        snake.addAll(cells)
        direction = dir
        pending.clear()
        score = 0
        tickCount = 0
        gameOverReason = null
    }

    private fun spawnFood() {
        val free = (0 until cols * rows)
            .asSequence()
            .map { GridPoint(it % cols, it / cols) }
            .filterNot { it in snake }
            .toList()
        if (free.isEmpty()) {
            // The snake has filled the whole board — that is a win.
            gameOver(BOARD_FULL_MESSAGE)
            return
        }
        food = free[random.nextInt(free.size)]
    }

    private fun gameOver(reason: String) {
        state = GameState.GAME_OVER
        gameOverReason = reason
    }

    companion object {
        const val DEFAULT_COLS = 18
        const val DEFAULT_ROWS = 24
        const val INITIAL_LENGTH = 3
        const val FOOD_POINTS = 10

        /** Baseline tick curve for the default ([Difficulty.NORMAL]) difficulty. */
        const val BASE_TICK_MILLIS = 300L
        const val MIN_TICK_MILLIS = 90L
        const val SPEEDUP_PER_SCORE = 6L

        /** Max number of direction presses buffered between ticks. */
        const val MAX_QUEUED_INPUTS = 3

        const val WALL_HIT_MESSAGE = "Hit the wall!"
        const val SELF_HIT_MESSAGE = "Bit yourself!"
        const val BOARD_FULL_MESSAGE = "Victory — the board is full!"
    }
}
