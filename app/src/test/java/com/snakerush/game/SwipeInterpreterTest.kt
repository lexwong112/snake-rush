package com.snakerush.game

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * JVM unit tests for the pure-logic gesture classifier used by GameView.
 */
class SwipeInterpreterTest {

    @Test
    fun rightSwipeMapsToRightDirection() {
        val interp = SwipeInterpreter()
        interp.onDown(10f, 10f)
        assertEquals(SwipeResult.Swipe(Direction.RIGHT), interp.onUp(110f, 12f))
    }

    @Test
    fun leftSwipeMapsToLeftDirection() {
        val interp = SwipeInterpreter()
        interp.onDown(110f, 10f)
        assertEquals(SwipeResult.Swipe(Direction.LEFT), interp.onUp(10f, 12f))
    }

    @Test
    fun downSwipeMapsToDownDirection() {
        val interp = SwipeInterpreter()
        interp.onDown(10f, 10f)
        assertEquals(SwipeResult.Swipe(Direction.DOWN), interp.onUp(12f, 110f))
    }

    @Test
    fun upSwipeDominatesOverHorizontalNoise() {
        val interp = SwipeInterpreter()
        interp.onDown(50f, 100f)
        assertEquals(SwipeResult.Swipe(Direction.UP), interp.onUp(55f, 10f))
    }

    @Test
    fun horizontalDominatesOverVerticalNoise() {
        val interp = SwipeInterpreter()
        interp.onDown(10f, 50f)
        assertEquals(SwipeResult.Swipe(Direction.RIGHT), interp.onUp(100f, 52f))
    }

    @Test
    fun tapWithinMaxDistanceReturnsTap() {
        val interp = SwipeInterpreter()
        interp.onDown(10f, 10f)
        assertEquals(SwipeResult.Tap, interp.onUp(14f, 12f))
    }

    @Test
    fun movementBetweenTapAndSwipeThresholdsReturnsNone() {
        val interp = SwipeInterpreter(minSwipeDistancePx = 100f, maxTapDistancePx = 5f)
        interp.onDown(0f, 0f)
        assertEquals(SwipeResult.None, interp.onUp(50f, 0f))
    }

    @Test
    fun onUpWithoutOnDownReturnsNone() {
        val interp = SwipeInterpreter()
        assertEquals(SwipeResult.None, interp.onUp(10f, 10f))
    }

    @Test
    fun cancelForgetsInFlightGesture() {
        val interp = SwipeInterpreter()
        interp.onDown(0f, 0f)
        interp.cancel()
        assertEquals(SwipeResult.None, interp.onUp(200f, 0f))
    }

    @Test
    fun gestureStateIsReusableAcrossGestures() {
        val interp = SwipeInterpreter()
        interp.onDown(0f, 0f)
        assertEquals(SwipeResult.Swipe(Direction.RIGHT), interp.onUp(100f, 0f))
        interp.onDown(0f, 0f)
        assertEquals(SwipeResult.Tap, interp.onUp(3f, 3f))
    }
}
