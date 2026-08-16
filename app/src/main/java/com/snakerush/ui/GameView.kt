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

    private val tickAccumulator = TickAccumulator()
    private val swipeInterpreter = SwipeInterpreter()
    private var lastFrameNanos = 0L
    private var loopRunning = false
    private var lastNotifiedScore = 0
    private var lastNotifiedState: GameState = game.state

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

    /** Size of one grid cell in px, derived in [onSizeChanged]. */
    private var cellSizePx = 0f
    private var boardLeft = 0f
    private var boardTop = 0f

    init {
        isClickable = true
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
        tickAccumulator.reset()
        lastFrameNanos = 0L
        lastNotifiedScore = 0
        lastNotifiedState = game.state
        onStateChanged?.invoke(game.state)
        onScoreChanged?.invoke(game.score)
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
        if (lastFrameNanos == 0L) {
            // First frame after (re)start: only establish the base timestamp.
            lastFrameNanos = frameTimeNanos
            return
        }
        val elapsedNanos = frameTimeNanos - lastFrameNanos
        lastFrameNanos = frameTimeNanos

        if (game.state == GameState.PLAYING) {
            val ticks = tickAccumulator.drain(elapsedNanos, game.tickIntervalMillis * NANOS_PER_MILLI)
            repeat(ticks) { game.update() }
        } else {
            // Outside PLAYING no time may accumulate, otherwise resuming play
            // would run a burst of backlogged ticks at once.
            tickAccumulator.reset()
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
        drawBoard(canvas)
        drawSnake(canvas)
        drawFood(canvas)
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
        val headRect = cellRect(game.head)
        val inset = cellSizePx * 0.10f
        headRect.inset(inset, inset)
        canvas.drawRoundRect(headRect, CORNER_RADIUS, CORNER_RADIUS, headPaint)
    }

    private fun drawFood(canvas: Canvas) {
        val r = cellRect(game.food)
        val radius = cellSizePx * 0.32f
        canvas.drawCircle(r.centerX(), r.centerY(), radius, foodPaint)
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

        val BG_COLOR = 0xFF0F1B24.toInt()
        val GRID_COLOR = 0xFF1E3140.toInt()
        val HEAD_COLOR = 0xFF4CAF50.toInt()
        val BODY_COLOR = 0xFF81C784.toInt()
        val FOOD_COLOR = 0xFFF44336.toInt()
    }
}
