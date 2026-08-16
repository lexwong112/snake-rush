package com.snakerush

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.TextView
import com.snakerush.game.Direction
import com.snakerush.ui.GameView

/**
 * Host activity for the Snake Rush game. Kept intentionally thin — all
 * gameplay lives in [com.snakerush.game.SnakeGame] and rendering/input in
 * [GameView]. This activity only wires the HUD and the on-screen D-pad to
 * the view, and owns the in-memory best score (persistence is Phase 3).
 */
class MainActivity : Activity() {

    private lateinit var gameView: GameView

    /** All-time best score for this process. Persisted in Phase 3. */
    private var bestScore = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        gameView = findViewById(R.id.gameView)

        val scoreText = findViewById<TextView>(R.id.scoreText)
        val bestScoreText = findViewById<TextView>(R.id.bestScoreText)
        scoreText.text = getString(R.string.score_label, 0)
        bestScoreText.text = getString(R.string.best_label, 0)
        gameView.onScoreChanged = { score ->
            if (score > bestScore) bestScore = score
            scoreText.text = getString(R.string.score_label, score)
            bestScoreText.text = getString(R.string.best_label, bestScore)
        }

        findViewById<View>(R.id.btnUp).setOnClickListener { gameView.pressDirection(Direction.UP) }
        findViewById<View>(R.id.btnDown).setOnClickListener { gameView.pressDirection(Direction.DOWN) }
        findViewById<View>(R.id.btnLeft).setOnClickListener { gameView.pressDirection(Direction.LEFT) }
        findViewById<View>(R.id.btnRight).setOnClickListener { gameView.pressDirection(Direction.RIGHT) }
    }

    override fun onResume() {
        super.onResume()
        gameView.startLoop()
    }

    override fun onPause() {
        gameView.stopLoop()
        super.onPause()
    }
}
