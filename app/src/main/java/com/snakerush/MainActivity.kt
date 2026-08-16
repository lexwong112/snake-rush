package com.snakerush

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.snakerush.game.Difficulty
import com.snakerush.game.Direction
import com.snakerush.game.GameState
import com.snakerush.ui.GameView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Host activity for the Snake Rush game. Kept intentionally thin — all
 * gameplay lives in [com.snakerush.game.SnakeGame] and rendering/input in
 * [GameView]. This activity wires the HUD, the on-screen D-pad and the three
 * overlay layers (menu / pause / game over) to the view, and owns the
 * persisted best score ([BestScoreStore]) and the selected difficulty.
 *
 * State sync: [GameView.onStateChanged] fires on every engine state
 * transition and [syncOverlays] maps the state to overlay visibility; the
 * overlay buttons drive the lifecycle through [GameView.startGame],
 * [GameView.resumeGame], [GameView.restartGame] and [GameView.newGame].
 */
class MainActivity : Activity() {

    private lateinit var gameView: GameView

    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val bestScoreStore by lazy { BestScoreStore(applicationContext) }

    /** Best score seen this session, seeded from DataStore and kept in sync. */
    private var bestScore = 0

    /** Difficulty the next round will use. */
    private var selectedDifficulty = Difficulty.NORMAL

    private lateinit var menuOverlay: View
    private lateinit var pauseOverlay: View
    private lateinit var gameOverOverlay: View
    private lateinit var dpad: View
    private lateinit var scoreText: TextView
    private lateinit var bestScoreText: TextView
    private lateinit var menuBestText: TextView
    private lateinit var gameOverScoreText: TextView
    private lateinit var gameOverBestText: TextView
    private lateinit var difficultyButtons: List<Button>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        gameView = findViewById(R.id.gameView)
        menuOverlay = findViewById(R.id.menuOverlay)
        pauseOverlay = findViewById(R.id.pauseOverlay)
        gameOverOverlay = findViewById(R.id.gameOverOverlay)
        dpad = findViewById(R.id.dpad)
        scoreText = findViewById(R.id.scoreText)
        bestScoreText = findViewById(R.id.bestScoreText)
        menuBestText = findViewById(R.id.menuBestText)
        gameOverScoreText = findViewById(R.id.gameOverScoreText)
        gameOverBestText = findViewById(R.id.gameOverBestText)

        scoreText.text = getString(R.string.score_label, 0)
        bestScoreText.text = getString(R.string.best_label, 0)

        // HUD + persistence: update the score label and persist any new best.
        gameView.onScoreChanged = { score ->
            scoreText.text = getString(R.string.score_label, score)
            if (score > bestScore) {
                bestScore = score
                uiScope.launch { bestScoreStore.recordScore(score) }
            }
            bestScoreText.text = getString(R.string.best_label, bestScore)
        }

        // Overlays follow the engine state machine.
        gameView.onStateChanged = { state -> syncOverlays(state) }

        // Seed the in-memory best from DataStore (keeps the HUD live). The menu
        // label is also refreshed so a late load lands while the menu is shown.
        uiScope.launch {
            bestScoreStore.bestScore.collect { persisted ->
                bestScore = persisted
                bestScoreText.text = getString(R.string.best_label, bestScore)
                if (gameView.game.state == GameState.MENU) {
                    menuBestText.text = getString(R.string.menu_best_label, bestScore)
                }
            }
        }

        setupMenu()
        setupDpad()
        syncOverlays(gameView.game.state)
    }

    private fun setupMenu() {
        val easy = findViewById<Button>(R.id.btnEasy)
        val normal = findViewById<Button>(R.id.btnNormal)
        val hard = findViewById<Button>(R.id.btnHard)
        difficultyButtons = listOf(easy, normal, hard)
        easy.setOnClickListener { selectDifficulty(Difficulty.EASY) }
        normal.setOnClickListener { selectDifficulty(Difficulty.NORMAL) }
        hard.setOnClickListener { selectDifficulty(Difficulty.HARD) }
        applyDifficultySelection()

        findViewById<View>(R.id.btnStart).setOnClickListener { gameView.startGame() }

        findViewById<View>(R.id.btnResume).setOnClickListener { gameView.resumeGame() }
        findViewById<View>(R.id.btnPauseRestart).setOnClickListener { restart() }
        findViewById<View>(R.id.btnPauseMenu).setOnClickListener { gameView.newGame(selectedDifficulty) }

        findViewById<View>(R.id.btnRestart).setOnClickListener { restart() }
        findViewById<View>(R.id.btnGameOverMenu).setOnClickListener { gameView.newGame(selectedDifficulty) }
    }

    private fun setupDpad() {
        findViewById<View>(R.id.btnUp).setOnClickListener { gameView.pressDirection(Direction.UP) }
        findViewById<View>(R.id.btnDown).setOnClickListener { gameView.pressDirection(Direction.DOWN) }
        findViewById<View>(R.id.btnLeft).setOnClickListener { gameView.pressDirection(Direction.LEFT) }
        findViewById<View>(R.id.btnRight).setOnClickListener { gameView.pressDirection(Direction.RIGHT) }
    }

    /** Starts a fresh round right away with the current difficulty. */
    private fun restart() {
        gameView.restartGame(selectedDifficulty)
    }

    private fun selectDifficulty(difficulty: Difficulty) {
        selectedDifficulty = difficulty
        applyDifficultySelection()
        // Re-roll the board so the new speed applies immediately.
        gameView.newGame(difficulty)
    }

    private fun applyDifficultySelection() {
        difficultyButtons.forEach { btn ->
            val selected = when (btn.id) {
                R.id.btnEasy -> selectedDifficulty == Difficulty.EASY
                R.id.btnNormal -> selectedDifficulty == Difficulty.NORMAL
                R.id.btnHard -> selectedDifficulty == Difficulty.HARD
                else -> false
            }
            btn.setBackgroundResource(
                if (selected) R.drawable.overlay_button_selected else R.drawable.overlay_button
            )
        }
    }

    /** Maps an engine state to exactly one visible overlay; HUD text is refreshed. */
    private fun syncOverlays(state: GameState) {
        menuOverlay.visibility = if (state == GameState.MENU) View.VISIBLE else View.GONE
        pauseOverlay.visibility = if (state == GameState.PAUSED) View.VISIBLE else View.GONE
        gameOverOverlay.visibility = if (state == GameState.GAME_OVER) View.VISIBLE else View.GONE
        dpad.visibility = if (state == GameState.PLAYING) View.VISIBLE else View.GONE
        when (state) {
            GameState.MENU -> menuBestText.text = getString(R.string.menu_best_label, bestScore)
            GameState.GAME_OVER -> {
                gameOverScoreText.text = getString(R.string.overlay_score_label, gameView.game.score)
                gameOverBestText.text = getString(R.string.overlay_best_label, bestScore)
            }
            else -> Unit
        }
    }

    override fun onResume() {
        super.onResume()
        gameView.startLoop()
    }

    override fun onPause() {
        gameView.stopLoop()
        super.onPause()
    }

    override fun onDestroy() {
        uiScope.cancel()
        super.onDestroy()
    }
}
