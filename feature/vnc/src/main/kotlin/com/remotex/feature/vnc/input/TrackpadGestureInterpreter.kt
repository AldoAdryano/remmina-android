package com.remotex.feature.vnc.input

sealed interface TrackpadResult {
    data class Pointer(val x: Int, val y: Int, val buttonsMask: Int) : TrackpadResult
    data class PointerButton(val buttonsMask: Int) : TrackpadResult
    data class Scroll(val horizontalSteps: Int, val verticalSteps: Int) : TrackpadResult
}

class TrackpadGestureInterpreter(
    private val pointerSpeed: Float = 1.25f,
) {
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
        val x = (currentRemoteX + dx * pointerSpeed).toInt().coerceIn(0, (framebufferWidth - 1).coerceAtLeast(0))
        val y = (currentRemoteY + dy * pointerSpeed).toInt().coerceIn(0, (framebufferHeight - 1).coerceAtLeast(0))
        return TrackpadResult.Pointer(x, y, buttonsMask)
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
