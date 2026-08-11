package com.remotex.feature.vnc.input

import kotlin.math.hypot
import kotlin.math.roundToInt

sealed interface TrackpadResult {
    data class Pointer(val x: Int, val y: Int, val buttonsMask: Int) : TrackpadResult
    data class PointerButton(val buttonsMask: Int) : TrackpadResult
    data class Scroll(val horizontalSteps: Int, val verticalSteps: Int) : TrackpadResult
}

class TrackpadGestureInterpreter(
    private val pointerSpeed: Float = 1.15f,
    private val acceleration: Float = 0.55f,
    private val accelerationDistance: Float = 80f,
) {
    private var pointerRemainderX = 0f
    private var pointerRemainderY = 0f
    private var scrollAccumulatorX = 0f
    private var scrollAccumulatorY = 0f

    init {
        require(pointerSpeed > 0f)
        require(acceleration >= 0f)
        require(accelerationDistance > 0f)
    }

    fun move(
        fingers: Int,
        dx: Float,
        dy: Float,
        currentRemoteX: Int,
        currentRemoteY: Int,
        framebufferWidth: Int,
        framebufferHeight: Int,
        buttonsMask: Int = 0,
    ): TrackpadResult.Pointer {
        require(fingers == 1)
        val distance = hypot(dx, dy)
        val accelerationRatio = (distance / accelerationDistance).coerceIn(0f, 1f)
        val multiplier = pointerSpeed * (1f + acceleration * accelerationRatio)

        val rawDeltaX = dx * multiplier + pointerRemainderX
        val rawDeltaY = dy * multiplier + pointerRemainderY
        val deltaX = rawDeltaX.roundToInt()
        val deltaY = rawDeltaY.roundToInt()

        val maxX = (framebufferWidth - 1).coerceAtLeast(0)
        val maxY = (framebufferHeight - 1).coerceAtLeast(0)
        val unclampedX = currentRemoteX + deltaX
        val unclampedY = currentRemoteY + deltaY
        val x = unclampedX.coerceIn(0, maxX)
        val y = unclampedY.coerceIn(0, maxY)

        pointerRemainderX = if (x == unclampedX) rawDeltaX - deltaX else 0f
        pointerRemainderY = if (y == unclampedY) rawDeltaY - deltaY else 0f

        return TrackpadResult.Pointer(x, y, buttonsMask)
    }

    fun scroll(dx: Float, dy: Float, stepPx: Float): TrackpadResult.Scroll {
        require(stepPx > 0f)
        // Natural touchpad direction: fingers move up -> remote wheel moves down,
        // fingers move down -> remote wheel moves up.
        scrollAccumulatorX -= dx
        scrollAccumulatorY -= dy

        val horizontalSteps = (scrollAccumulatorX / stepPx).toInt()
        val verticalSteps = (scrollAccumulatorY / stepPx).toInt()
        if (horizontalSteps != 0) scrollAccumulatorX -= horizontalSteps * stepPx
        if (verticalSteps != 0) scrollAccumulatorY -= verticalSteps * stepPx
        return TrackpadResult.Scroll(horizontalSteps, verticalSteps)
    }

    fun resetGesture() {
        pointerRemainderX = 0f
        pointerRemainderY = 0f
        scrollAccumulatorX = 0f
        scrollAccumulatorY = 0f
    }

    fun leftTap(): List<TrackpadResult.PointerButton> = clickMask(LEFT_BUTTON)
    fun rightTap(): List<TrackpadResult.PointerButton> = clickMask(RIGHT_BUTTON)
    fun dragMask(active: Boolean): Int = if (active) LEFT_BUTTON else 0

    private fun clickMask(mask: Int) = listOf(TrackpadResult.PointerButton(mask), TrackpadResult.PointerButton(0))

    companion object {
        const val LEFT_BUTTON = 1
        const val MIDDLE_BUTTON = 2
        const val RIGHT_BUTTON = 4
        const val WHEEL_UP = 8
        const val WHEEL_DOWN = 16
        const val WHEEL_LEFT = 32
        const val WHEEL_RIGHT = 64
    }
}
