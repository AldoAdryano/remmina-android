package com.remotex.feature.vnc.input

/**
 * Prevents a one-finger pointer move from being emitted after a gesture has
 * already transitioned into multi-touch. The pointer remains suppressed until
 * every finger is lifted and a fresh ACTION_DOWN starts the next gesture.
 */
class TrackpadTouchGuard {
    private var hadMultiTouch = false

    fun beginGesture() {
        hadMultiTouch = false
    }

    fun observePointerCount(pointerCount: Int) {
        require(pointerCount >= 1)
        if (pointerCount > 1) hadMultiTouch = true
    }

    fun canMovePointer(pointerCount: Int): Boolean = pointerCount == 1 && !hadMultiTouch

    fun endGesture() {
        hadMultiTouch = false
    }
}
