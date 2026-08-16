package com.snakerush.game

/** Top-level lifecycle of the game. */
enum class GameState {
    /** Shown before the first move (waiting for a swipe/button). */
    MENU,

    /** The snake is moving. */
    PLAYING,

    /** Game frozen; overlay shown. */
    PAUSED,

    /** The snake hit a wall or itself; overlay shown. */
    GAME_OVER,
}
