package com.snakerush.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for the pure-Kotlin [SnakeGame] engine.
 * Run with: ./gradlew test
 */
class SnakeGameTest {

    @Test
    fun initialLayoutHasSnakeHeadingRight() {
        val game = SnakeGame(18, 24)
        assertEquals(GameState.MENU, game.state)
        assertEquals(SnakeGame.INITIAL_LENGTH, game.snake.size)
        assertEquals(Direction.RIGHT, game.direction)
        val head = game.head
        assertEquals(GridPoint(head.x - 1, head.y), game.snake[1])
        assertEquals(GridPoint(head.x - 2, head.y), game.snake[2])
    }

    @Test
    fun updateIsNoOpUnlessPlaying() {
        val game = SnakeGame()
        val before = game.tickCount
        assertFalse(game.update())
        assertEquals(before, game.tickCount)

        game.start()
        assertTrue(game.update())
        assertEquals(before + 1, game.tickCount)
    }

    @Test
    fun reverseDirectionIsIgnored() {
        val game = SnakeGame()
        game.start()
        game.setDirection(Direction.LEFT) // opposite of the initial RIGHT
        game.update()
        assertEquals(Direction.RIGHT, game.direction)
    }

    @Test
    fun queuedTurnIsAppliedOnNextTick() {
        val game = SnakeGame()
        game.start()
        game.setDirection(Direction.DOWN)
        game.update()
        assertEquals(Direction.DOWN, game.direction)
    }

    @Test
    fun quickDoubleTurnQueuesBothDirections() {
        val game = SnakeGame()
        game.start()
        game.setDirection(Direction.DOWN)
        game.setDirection(Direction.LEFT) // would reverse vs RIGHT, legal after DOWN
        game.update()
        assertEquals(Direction.DOWN, game.direction)
        game.update()
        assertEquals(Direction.LEFT, game.direction)
    }

    @Test
    fun hittingTheWallEndsTheGame() {
        val game = SnakeGame(18, 24)
        game.start()
        repeat(100) { game.update() }
        assertEquals(GameState.GAME_OVER, game.state)
        assertEquals(SnakeGame.WALL_HIT_MESSAGE, game.gameOverReason)
    }

    @Test
    fun eatingFoodGrowsSnakeAndIncreasesScore() {
        val game = SnakeGame(18, 24)
        game.start()
        val front = game.head.translated(Direction.RIGHT)
        game.placeFoodAt(front)
        val sizeBefore = game.snake.size
        val scoreBefore = game.score

        game.update()

        assertEquals(sizeBefore + 1, game.snake.size)
        assertEquals(scoreBefore + SnakeGame.FOOD_POINTS, game.score)
        assertEquals(front, game.head)
    }

    @Test
    fun foodAlwaysSpawnsOnAFreeCellInsideTheBoard() {
        val game = SnakeGame(5, 5)
        game.start()
        repeat(5) {
            game.update()
            assertTrue(game.food.x in 0 until game.cols)
            assertTrue(game.food.y in 0 until game.rows)
            assertTrue(game.food !in game.snake)
        }
    }

    @Test
    fun movingIntoTheVacatingTailCellIsSafe() {
        val game = SnakeGame(10, 10)
        game.loadStateForTesting(
            listOf(GridPoint(5, 5), GridPoint(5, 6), GridPoint(6, 6), GridPoint(6, 5)),
            Direction.RIGHT
        )
        game.start()
        game.update()
        assertEquals(GameState.PLAYING, game.state)
        assertEquals(GridPoint(6, 5), game.head)
    }

    @Test
    fun bitingTheBodyEndsTheGame() {
        val game = SnakeGame(10, 10)
        game.loadStateForTesting(
            listOf(GridPoint(5, 5), GridPoint(5, 6), GridPoint(6, 6), GridPoint(6, 5)),
            Direction.DOWN
        )
        game.start()
        game.update()
        assertEquals(GameState.GAME_OVER, game.state)
        assertEquals(SnakeGame.SELF_HIT_MESSAGE, game.gameOverReason)
    }

    @Test
    fun speedIncreasesAsScoreGrowsButIsClamped() {
        val game = SnakeGame()
        game.start()
        repeat(5) {
            game.placeFoodAt(game.head.translated(Direction.RIGHT))
            game.update()
        }
        assertTrue(game.score > 0)
        assertTrue(game.tickIntervalMillis < SnakeGame.BASE_TICK_MILLIS)
        assertTrue(game.tickIntervalMillis >= SnakeGame.MIN_TICK_MILLIS)
    }

    @Test
    fun defaultDifficultyIsNormalAndMatchesBaselineFormula() {
        val game = SnakeGame()
        assertEquals(Difficulty.NORMAL, game.difficulty)
        assertEquals(SnakeGame.BASE_TICK_MILLIS, game.tickIntervalMillis)
    }

    @Test
    fun hardDifficultyStartsFasterThanNormal() {
        val normal = SnakeGame(difficulty = Difficulty.NORMAL)
        val hard = SnakeGame(difficulty = Difficulty.HARD)
        assertTrue(hard.tickIntervalMillis < normal.tickIntervalMillis)
    }

    @Test
    fun easyDifficultyStartsSlowerThanNormal() {
        val normal = SnakeGame(difficulty = Difficulty.NORMAL)
        val easy = SnakeGame(difficulty = Difficulty.EASY)
        assertTrue(easy.tickIntervalMillis > normal.tickIntervalMillis)
    }

    @Test
    fun eachDifficultyRespectsItsOwnSpeedFloor() {
        for (difficulty in Difficulty.entries) {
            // Wide board so the snake never reaches a wall while feeding.
            val game = SnakeGame(60, 60, difficulty)
            game.start()
            repeat(25) {
                game.placeFoodAt(game.head.translated(Direction.RIGHT))
                game.update()
            }
            assertTrue(game.score > 0)
            assertTrue(
                "expected tick >= ${difficulty.minTickMillis} for $difficulty, was ${game.tickIntervalMillis}",
                game.tickIntervalMillis >= difficulty.minTickMillis
            )
            assertTrue(game.tickIntervalMillis <= difficulty.baseTickMillis)
        }
    }
}
