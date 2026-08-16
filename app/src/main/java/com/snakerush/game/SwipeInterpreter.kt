package com.snakerush.game

import kotlin.math.abs
import kotlin.math.hypot

/** Outcome of a completed touch gesture, as classified by [SwipeInterpreter]. */
sealed class SwipeResult {
    /** The gesture was too small / not a deliberate swipe and not a tap. */
    object None : SwipeResult()

    /** The finger stayed (almost) still — treated as a tap. */
    object Tap : SwipeResult()

    /** The finger travelled past the swipe threshold in a dominant axis. */
    data class Swipe(val direction: Direction) : SwipeResult()
}

/**
 * Pure-logic classifier for touch gestures. The Android view layer feeds raw
 * touch coordinates in; this returns a [SwipeResult] on release.
 *
 * Kept free of Android types so it can be unit-tested on the JVM.
 */
class SwipeInterpreter(
    /** Minimum travel distance (px) for a swipe to be recognized. */
    private val minSwipeDistancePx: Float = DEFAULT_MIN_SWIPE_DISTANCE_PX,
    /** Movement below this distance (px) counts as a tap, not a swipe. */
    private val maxTapDistancePx: Float = DEFAULT_MAX_TAP_DISTANCE_PX,
) {

    private var downX = 0f
    private var downY = 0f
    private var tracking = false

    /** Records the start of a gesture (ACTION_DOWN). */
    fun onDown(x: Float, y: Float) {
        downX = x
        downY = y
        tracking = true
    }

    /**
     * Classifies the gesture on release (ACTION_UP).
     * Returns [SwipeResult.None] if no [onDown] was seen.
     */
    fun onUp(x: Float, y: Float): SwipeResult {
        if (!tracking) return SwipeResult.None
        tracking = false
        val dx = x - downX
        val dy = y - downY
        val distance = hypot(dx, dy)
        if (distance < maxTapDistancePx) return SwipeResult.Tap
        if (distance < minSwipeDistancePx) return SwipeResult.None
        return if (abs(dx) > abs(dy)) {
            SwipeResult.Swipe(if (dx > 0) Direction.RIGHT else Direction.LEFT)
        } else {
            SwipeResult.Swipe(if (dy > 0) Direction.DOWN else Direction.UP)
        }
    }

    /** Forgets the in-flight gesture (ACTION_CANCEL). */
    fun cancel() {
        tracking = false
    }

    companion object {
        const val DEFAULT_MIN_SWIPE_DISTANCE_PX = 48f
        const val DEFAULT_MAX_TAP_DISTANCE_PX = 16f
    }
}
