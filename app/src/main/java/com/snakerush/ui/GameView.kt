package com.snakerush.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import com.snakerush.R
import com.snakerush.game.Difficulty
import com.snakerush.game.Direction
import com.snakerush.game.GameState
import com.snakerush.game.GridPoint
import com.snakerush.game.SnakeGame
import com.snakerush.game.SwipeInterpreter
import com.snakerush.game.SwipeResult
import com.snakerush.game.TickAccumulator
import kotlin.math.cos
import kotlin.math.sin

/**
 * Custom [View] that renders the current [SnakeGame] state and drives it.
 *
 * Phase 3: a [Choreographer]-driven loop redraws every frame and advances the
 * engine at its fixed tick interval via a [TickAccumulator]. Input arrives
 * either as swipe gestures on this view or as D-pad presses routed through
 * [pressDirection]; a tap toggles pause/resume. The activity keeps its menu /
 * pause / game-over overlays in sync by listening to [onStateChanged] and
 * drives round lifecycle through [startGame], [resumeGame], [newGame] and
 * [restartGame].
 *
 * Note: [onDraw] only reads engine state — all mutations happen in the frame
 * callback [advance], never during drawing.
 */
class GameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    /** The game instance this view renders. Replaced by [newGame]/[restartGame]. */
    var game: SnakeGame = SnakeGame()
        private set

    /** Invoked whenever the score changes (after the tick that scored). */
    var onScoreChanged: ((score: Int) -> Unit)? = null

    /** Invoked whenever [game]'s state changes (MENU → PLAYING, → GAME_OVER, …). */
    var onStateChanged: ((state: GameState) -> Unit)? = null

    /** Invoked when the snake eats food, with the cell that was consumed. */
    var onFoodEaten: ((cell: GridPoint) -> Unit)? = null

    /** Invoked when the round ends (wall hit, self bite, board full). */
    var onGameOver: ((reason: String) -> Unit)? = null

    private val tickAccumulator = TickAccumulator()
    private val swipeInterpreter = SwipeInterpreter()
    private var lastFrameNanos = 0L
    private var loopRunning = false
    private var lastNotifiedScore = 0
    private var lastNotifiedState: GameState = game.state

    /**
     * Wall-clock (Choreographer vsync) time of the frame currently being
     * drawn, in nanos. All effects below age themselves against this absolute
     * timestamp, never against tick counts, so the fixed-tick simulation and
     * the frame-rate-based cosmetics stay decoupled.
     */
    private var renderTimeNanos = 0L

    /**
     * Animation clock in nanos that advances only while the engine is
     * [GameState.PLAYING] and is reset otherwise — same convention as
     * [TickAccumulator] — so pausing can never make a looping effect jump
     * (and no time accumulates in MENU / PAUSED / GAME_OVER).
     */
    private var animTimeNanos = 0L

    /** Cell + start time of the food-eaten particle flash; null when idle. */
    private var burstCell: GridPoint? = null
    private var burstStartNanos = 0L

    /** Vsync time of the frame the round ended; drives the death shake. */
    private var gameOverStartNanos = 0L

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!loopRunning) return
            advance(frameTimeNanos)
            invalidate()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private val bgPaint = Paint().apply { color = BG_COLOR }
    private val gridPaint = Paint().apply {
        color = GRID_COLOR
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val headPaint = Paint().apply { color = HEAD_COLOR }
    private val bodyPaint = Paint().apply { color = BODY_COLOR }
    private val foodPaint = Paint().apply { color = FOOD_COLOR }
    private val eyeWhitePaint = Paint().apply { color = EYE_WHITE_COLOR }
    private val eyePupilPaint = Paint().apply { color = EYE_PUPIL_COLOR }
    private val burstPaint = Paint().apply { style = Paint.Style.STROKE }

    /** Size of one grid cell in px, derived in [onSizeChanged]. */
    private var cellSizePx = 0f
    private var boardLeft = 0f
    private var boardTop = 0f

    init {
        isClickable = true
        attachEngineCallbacks()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startLoop()
    }

    override fun onDetachedFromWindow() {
        stopLoop()
        super.onDetachedFromWindow()
    }

    /** Starts the Choreographer tick loop. Safe to call repeatedly. */
    fun startLoop() {
        if (loopRunning) return
        loopRunning = true
        tickAccumulator.reset()
        lastFrameNanos = 0L
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    /**
     * Stops the Choreographer tick loop; the game freezes where it is.
     * The next [startLoop] re-bases its clock, so a long pause never bursts a
     * backlog of ticks when play resumes.
     */
    fun stopLoop() {
        loopRunning = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    /**
     * Applies a direction press from either input source (swipe or D-pad).
     * The first press in MENU starts the game, and the press itself is queued.
     * No-op in PAUSED / GAME_OVER. (With the menu overlay in front the Start
     * button is the primary path, but this safety net keeps the game playable
     * if a press ever reaches the view while it is in MENU.)
     */
    fun pressDirection(dir: Direction) {
        when (game.state) {
            GameState.MENU -> {
                game.start()
                game.setDirection(dir)
            }
            GameState.PLAYING -> game.setDirection(dir)
            else -> Unit
        }
        syncState()
    }

    /**
     * Tap action: toggles between PLAYING and PAUSED. State transitions from
     * MENU / GAME_OVER are driven by the overlay buttons instead, never here.
     */
    fun togglePause() {
        when (game.state) {
            GameState.PLAYING -> game.pause()
            GameState.PAUSED -> game.resume()
            else -> Unit
        }
        syncState()
    }

    /** Starts the game from the menu (menu overlay "Start" button). */
    fun startGame() {
        game.start()
        syncState()
    }

    /** Resumes a paused game (pause overlay "Resume" button). */
    fun resumeGame() {
        game.resume()
        syncState()
    }

    /**
     * Replaces the engine with a fresh round on [difficulty] and returns to
     * the menu without starting. Used by the difficulty selector and by the
     * "Menu" buttons on the pause / game-over overlays.
     */
    fun newGame(difficulty: Difficulty = game.difficulty) {
        game = SnakeGame(difficulty = difficulty)
        attachEngineCallbacks()
        tickAccumulator.reset()
        animTimeNanos = 0L
        burstCell = null
        lastFrameNanos = 0L
        lastNotifiedScore = 0
        lastNotifiedState = game.state
        onStateChanged?.invoke(game.state)
        onScoreChanged?.invoke(game.score)
    }

    /**
     * Wires the engine's pure-logic events to this view's animation state and
     * to the forwarded callbacks. Must be called whenever [game] is replaced.
     */
    private fun attachEngineCallbacks() {
        game.onFoodEaten = { cell ->
            burstCell = cell
            burstStartNanos = renderTimeNanos
            onFoodEaten?.invoke(cell)
        }
        game.onGameOver = { reason ->
            onGameOver?.invoke(reason)
        }
    }

    /** Starts a fresh round immediately (overlay "Restart" buttons). */
    fun restartGame(difficulty: Difficulty = game.difficulty) {
        newGame(difficulty)
        game.start()
        syncState()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                swipeInterpreter.onDown(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_UP -> {
                when (val result = swipeInterpreter.onUp(event.x, event.y)) {
                    is SwipeResult.Swipe -> pressDirection(result.direction)
                    SwipeResult.Tap -> togglePause()
                    SwipeResult.None -> Unit
                }
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                swipeInterpreter.cancel()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun advance(frameTimeNanos: Long) {
        renderTimeNanos = frameTimeNanos
        if (lastFrameNanos == 0L) {
            // First frame after (re)start: only establish the base timestamp.
            lastFrameNanos = frameTimeNanos
            return
        }
        val elapsedNanos = frameTimeNanos - lastFrameNanos
        lastFrameNanos = frameTimeNanos

        if (game.state == GameState.PLAYING) {
            animTimeNanos += elapsedNanos
            val ticks = tickAccumulator.drain(elapsedNanos, game.tickIntervalMillis * NANOS_PER_MILLI)
            repeat(ticks) { game.update() }
        } else {
            // Outside PLAYING no time may accumulate: not for the tick
            // accumulator (resuming would burst backlogged ticks) and not for
            // the animation clock either (looping effects must not jump after
            // a pause).
            tickAccumulator.reset()
            animTimeNanos = 0L
        }

        if (burstCell != null && renderTimeNanos - burstStartNanos > BURST_DURATION_NANOS) {
            burstCell = null
        }
        if (game.state == GameState.GAME_OVER && lastNotifiedState != GameState.GAME_OVER) {
            // Round just ended: anchor the death shake to this vsync time.
            gameOverStartNanos = frameTimeNanos
        }

        if (game.score != lastNotifiedScore) {
            lastNotifiedScore = game.score
            onScoreChanged?.invoke(game.score)
        }
        syncState()
    }

    /** Fires [onStateChanged] once per actual state transition. */
    private fun syncState() {
        if (game.state == lastNotifiedState) return
        lastNotifiedState = game.state
        onStateChanged?.invoke(game.state)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val pad = PAD_DP.toFloat()
        val topOffset = TOP_OFFSET_PX.toFloat()
        val bottomReserve = resources.getDimension(R.dimen.board_bottom_reserve)
        val availableWidth = w - 2f * pad
        val availableHeight = h - 2f * pad - topOffset - bottomReserve
        cellSizePx = minOf(availableWidth / game.cols, availableHeight / game.rows)
        val boardWidth = cellSizePx * game.cols
        val boardHeight = cellSizePx * game.rows
        boardLeft = (w - boardWidth) / 2f
        boardTop = pad + topOffset + (availableHeight - boardHeight) / 2f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(BG_COLOR)
        val (shakeDx, shakeDy) = deathShakeOffset()
        canvas.save()
        canvas.translate(shakeDx, shakeDy)
        drawBoard(canvas)
        drawSnake(canvas)
        drawFood(canvas)
        drawFoodBurst(canvas)
        canvas.restore()
    }

    /**
     * Board offset (px) for the brief shake right after the round ends.
     * Decays to zero over [SHAKE_DURATION_NANOS]; anchored to absolute
     * [renderTimeNanos] so it plays exactly once, at the moment of death.
     */
    private fun deathShakeOffset(): Pair<Float, Float> {
        if (game.state != GameState.GAME_OVER) return 0f to 0f
        val ageMs = (renderTimeNanos - gameOverStartNanos) / NANOS_PER_MILLI
        if (ageMs < 0f || ageMs > SHAKE_DURATION_MS) return 0f to 0f
        val progress = ageMs / SHAKE_DURATION_MS
        val amplitude = MAX_SHAKE_PX * (1f - progress)
        return amplitude * sin(ageMs * SHAKE_FREQ_RAD_PER_MS) to
            amplitude * cos(ageMs * SHAKE_FREQ_RAD_PER_MS * 1.3f)
    }

    private fun drawBoard(canvas: Canvas) {
        val right = boardLeft + game.cols * cellSizePx
        val bottom = boardTop + game.rows * cellSizePx
        canvas.drawRect(boardLeft, boardTop, right, bottom, gridPaint)
        for (x in 1 until game.cols) {
            val lineX = boardLeft + x * cellSizePx
            canvas.drawLine(lineX, boardTop, lineX, bottom, gridPaint)
        }
        for (y in 1 until game.rows) {
            val lineY = boardTop + y * cellSizePx
            canvas.drawLine(boardLeft, lineY, right, lineY, gridPaint)
        }
    }

    private fun drawSnake(canvas: Canvas) {
        if (game.snake.isEmpty()) return
        game.snake.drop(1).forEach { cell ->
            canvas.drawRoundRect(cellRect(cell), CORNER_RADIUS, CORNER_RADIUS, bodyPaint)
        }
        drawHead(canvas)
    }

    private fun drawHead(canvas: Canvas) {
        val headRect = cellRect(game.head)
        var inset = cellSizePx * 0.10f
        if (game.state == GameState.PLAYING) {
            // Subtle "breathing" bounce: inset oscillates around its base so
            // the head gently pulses at the animation-clock frequency.
            val animMs = animTimeNanos / NANOS_PER_MILLI
            inset -= cellSizePx * HEAD_BOUNCE_AMPLITUDE * sin(animMs * HEAD_PULSE_RAD_PER_MS)
        }
        headRect.inset(inset, inset)
        canvas.drawRoundRect(headRect, CORNER_RADIUS, CORNER_RADIUS, headPaint)

        // Eyes look along the travel direction (perp = 90° clockwise).
        val fx = game.direction.dx.toFloat()
        val fy = game.direction.dy.toFloat()
        val px = -fy
        val py = fx
        val cx = headRect.centerX()
        val cy = headRect.centerY()
        val forward = cellSizePx * 0.16f
        val lateral = cellSizePx * 0.13f
        val eyeRadius = cellSizePx * 0.10f
        for (side in listOf(-1f, 1f)) {
            val ex = cx + fx * forward + px * lateral * side
            val ey = cy + fy * forward + py * lateral * side
            canvas.drawCircle(ex, ey, eyeRadius, eyeWhitePaint)
            canvas.drawCircle(
                ex + fx * eyeRadius * 0.4f,
                ey + fy * eyeRadius * 0.4f,
                eyeRadius * 0.5f,
                eyePupilPaint,
            )
        }
    }

    private fun drawFood(canvas: Canvas) {
        val r = cellRect(game.food)
        var radius = cellSizePx * 0.32f
        if (game.state == GameState.PLAYING) {
            val animMs = animTimeNanos / NANOS_PER_MILLI
            radius *= 1f + FOOD_PULSE_AMPLITUDE * sin(animMs * FOOD_PULSE_RAD_PER_MS)
        }
        canvas.drawCircle(r.centerX(), r.centerY(), radius, foodPaint)
    }

    /** Expanding, fading ring at the cell that was just eaten. */
    private fun drawFoodBurst(canvas: Canvas) {
        val cell = burstCell ?: return
        val ageMs = (renderTimeNanos - burstStartNanos) / NANOS_PER_MILLI
        if (ageMs < 0f) return
        val progress = (ageMs / BURST_DURATION_MS).coerceIn(0f, 1f)
        val r = cellRect(cell)
        val maxRadius = cellSizePx * 0.70f
        burstPaint.color = FOOD_COLOR
        burstPaint.strokeWidth = cellSizePx * 0.10f * (1f - progress)
        burstPaint.alpha = (255 * (1f - progress)).toInt()
        canvas.drawCircle(r.centerX(), r.centerY(), cellSizePx * 0.18f + (maxRadius - cellSizePx * 0.18f) * progress, burstPaint)
        burstPaint.alpha = 255
    }

    private fun cellRect(p: GridPoint): RectF {
        val left = boardLeft + p.x * cellSizePx
        val top = boardTop + p.y * cellSizePx
        return RectF(left, top, left + cellSizePx, top + cellSizePx)
    }

    private companion object {
        const val PAD_DP = 16f
        const val TOP_OFFSET_PX = 56f
        const val CORNER_RADIUS = 8f
        const val NANOS_PER_MILLI = 1_000_000L

        // --- animation tuning (all frame-time based, see advance/onDraw) ---
        const val FOOD_PULSE_AMPLITUDE = 0.15f
        const val FOOD_PULSE_RAD_PER_MS = 0.006f
        const val HEAD_BOUNCE_AMPLITUDE = 0.04f
        const val HEAD_PULSE_RAD_PER_MS = 0.008f
        const val BURST_DURATION_MS = 350f
        const val SHAKE_DURATION_MS = 450f
        const val MAX_SHAKE_PX = 10f
        const val SHAKE_FREQ_RAD_PER_MS = 0.035f
        const val BURST_DURATION_NANOS = (BURST_DURATION_MS * 1_000_000).toLong()

        val BG_COLOR = 0xFF0F1B24.toInt()
        val GRID_COLOR = 0xFF1E3140.toInt()
        val HEAD_COLOR = 0xFF4CAF50.toInt()
        val BODY_COLOR = 0xFF81C784.toInt()
        val FOOD_COLOR = 0xFFF44336.toInt()
        val EYE_WHITE_COLOR = 0xFFFFFFFF.toInt()
        val EYE_PUPIL_COLOR = 0xFF1B2A3A.toInt()
    }
}
