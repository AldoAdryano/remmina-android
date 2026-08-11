package com.remotex.feature.vnc.domain

data class VncConnectionSpec(
    val host: String,
    val port: Int = 5900,
    val password: CharArray? = null,
    val shared: Boolean = true,
)

sealed interface VncSessionState {
    data object Idle : VncSessionState
    data object Connecting : VncSessionState
    data class Connected(val width: Int, val height: Int, val desktopName: String) : VncSessionState
    data class Reconnecting(val attempt: Int) : VncSessionState
    data class Failed(val reason: String, val retryable: Boolean = true) : VncSessionState
    data object Closed : VncSessionState
}

data class VncFrame(
    val width: Int,
    val height: Int,
    /** Pixels for the dirty rectangle, tightly packed row-by-row. */
    val argb: IntArray,
    val dirtyLeft: Int = 0,
    val dirtyTop: Int = 0,
    val dirtyRight: Int = width,
    val dirtyBottom: Int = height,
)

sealed interface VncInputEvent {
    data class Pointer(val x: Int, val y: Int, val buttonsMask: Int) : VncInputEvent
    data class Scroll(val deltaX: Int, val deltaY: Int, val x: Int, val y: Int) : VncInputEvent
    data class Key(val keysym: Int, val down: Boolean) : VncInputEvent
    data class Clipboard(val text: String) : VncInputEvent
}

enum class VncScaleMode { FIT_SCREEN, FILL_SCREEN, ORIGINAL_SIZE, STRETCH }
enum class VncInputMode { TRACKPAD, DIRECT_TOUCH }
