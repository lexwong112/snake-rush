package com.snakerush.game

/**
 * Cardinal directions on the board grid. [dx]/[dy] are the grid-space
 * deltas applied when the snake moves one cell in this direction.
 */
enum class Direction(val dx: Int, val dy: Int) {
    UP(0, -1),
    DOWN(0, 1),
    LEFT(-1, 0),
    RIGHT(1, 0);

    /** True if [other] points in exactly the opposite way (a 180° reversal). */
    fun isOpposite(other: Direction): Boolean =
        dx == -other.dx && dy == -other.dy
}
