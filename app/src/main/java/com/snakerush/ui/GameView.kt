package com.snakerush.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.snakerush.game.GameState
import com.snakerush.game.GridPoint
import com.snakerush.game.SnakeGame

/**
 * Custom [View] that renders the current [SnakeGame] state.
 *
 * Phase 1 ships only a static renderer so the app is runnable end-to-end:
 * it draws the initial board, snake and food. Phase 2 adds the
 * Choreographer-driven tick loop, swipe input and the score HUD wiring.
 */
class GameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    /** The single game instance this view renders. */
    val game: SnakeGame = SnakeGame()

    private val bgPaint = Paint().apply { color = BG_COLOR }
    private val gridPaint = Paint().apply {
        color = GRID_COLOR
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val headPaint = Paint().apply { color = HEAD_COLOR }
    private val bodyPaint = Paint().apply { color = BODY_COLOR }
    private val foodPaint = Paint().apply { color = FOOD_COLOR }
    private val hintPaint = Paint().apply {
        color = TEXT_COLOR
        textSize = 30f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    /** Size of one grid cell in px, derived in [onSizeChanged]. */
    private var cellSizePx = 0f
    private var boardLeft = 0f
    private var boardTop = 0f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val pad = PAD_DP.toFloat()
        val topOffset = TOP_OFFSET_PX.toFloat()
        val availableWidth = w - 2f * pad
        val availableHeight = h - 2f * pad - topOffset
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
        if (game.state == GameState.MENU) {
            canvas.drawText("Swipe to start", width / 2f, height * 0.62f, hintPaint)
        }
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

        val BG_COLOR = 0xFF0F1B24.toInt()
        val GRID_COLOR = 0xFF1E3140.toInt()
        val HEAD_COLOR = 0xFF4CAF50.toInt()
        val BODY_COLOR = 0xFF81C784.toInt()
        val FOOD_COLOR = 0xFFF44336.toInt()
        val TEXT_COLOR = 0xFFFFFFFF.toInt()
    }
}
