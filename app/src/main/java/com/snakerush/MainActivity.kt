package com.snakerush

import android.app.Activity
import android.os.Bundle
import com.snakerush.ui.GameView

/**
 * Host activity for the Snake Rush game. Kept intentionally thin — all
 * gameplay lives in [com.snakerush.game.SnakeGame] and rendering in
 * [GameView]. Phase 2 wires up the game loop and input here / in GameView.
 */
class MainActivity : Activity() {

    private lateinit var gameView: GameView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        gameView = findViewById(R.id.gameView)
    }
}
