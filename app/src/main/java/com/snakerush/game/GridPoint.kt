package com.snakerush.game

/**
 * A single cell on the game board. Plain value type (no Android dependency)
 * so the engine can be unit-tested on the JVM.
 */
data class GridPoint(val x: Int, val y: Int) {

    /** Cell reached by moving one step in [dir] from this cell. */
    fun translated(dir: Direction): GridPoint =
        GridPoint(x + dir.dx, y + dir.dy)

    operator fun plus(dir: Direction): GridPoint = translated(dir)
}
