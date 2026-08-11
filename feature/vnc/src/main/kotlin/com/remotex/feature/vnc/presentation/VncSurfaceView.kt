package com.remotex.feature.vnc.presentation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import com.remotex.feature.vnc.domain.VncFrame
import com.remotex.feature.vnc.domain.VncInputEvent
import com.remotex.feature.vnc.domain.VncInputMode
import com.remotex.feature.vnc.domain.VncScaleMode
import com.remotex.feature.vnc.input.TrackpadGestureInterpreter
import kotlin.math.hypot

class VncSurfaceView(context: Context) : View(context) {
    var onInput: (VncInputEvent) -> Unit = {}
    var onKeyboardRequested: () -> Unit = {}
    var inputMode: VncInputMode = VncInputMode.TRACKPAD
    var scaleMode: VncScaleMode = VncScaleMode.FIT_SCREEN
        set(value) { field = value; invalidate() }

    private var bitmap: Bitmap? = null
    private var lastFrame: VncFrame? = null
    private var remoteWidth = 1
    private var remoteHeight = 1
    private var pointerX = 0
    private var pointerY = 0
    private var zoom = 1f
    private var lastX = 0f
    private var lastY = 0f
    private var gestureOriginX = 0f
    private var gestureOriginY = 0f
    private var maxPointers = 0
    private var gestureMoved = false
    private var dragging = false
    private var twoFingerStart = 0L
    private var scrollAccumulatorX = 0f
    private var scrollAccumulatorY = 0f
    private val scaledTouchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val trackpad = TrackpadGestureInterpreter()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            zoom = (zoom * detector.scaleFactor).coerceIn(0.5f, 5f)
            invalidate()
            return true
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (maxPointers <= 1) {
                click(1)
                click(1)
            }
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            if (maxPointers <= 1) {
                dragging = true
                onInput(VncInputEvent.Pointer(pointerX, pointerY, 1))
            }
        }
    })

    fun setFrame(frame: VncFrame) {
        if (frame === lastFrame) return
        lastFrame = frame
        remoteWidth = frame.width
        remoteHeight = frame.height
        pointerX = pointerX.coerceIn(0, remoteWidth - 1)
        pointerY = pointerY.coerceIn(0, remoteHeight - 1)
        bitmap?.recycle()
        bitmap = Bitmap.createBitmap(frame.argb, frame.width, frame.height, Bitmap.Config.ARGB_8888)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.BLACK)
        bitmap?.let { canvas.drawBitmap(it, null, destinationRect(), paint) }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        maxPointers = maxOf(maxPointers, event.pointerCount)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                maxPointers = 1
                gestureMoved = false
                dragging = false
                lastX = event.x
                lastY = event.y
                gestureOriginX = event.x
                gestureOriginY = event.y
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount == 2) twoFingerStart = System.currentTimeMillis()
                lastX = averageX(event)
                lastY = averageY(event)
                gestureOriginX = lastX
                gestureOriginY = lastY
            }

            MotionEvent.ACTION_MOVE -> {
                val x = if (event.pointerCount > 1) averageX(event) else event.x
                val y = if (event.pointerCount > 1) averageY(event) else event.y
                val dx = x - lastX
                val dy = y - lastY
                if (hypot(x - gestureOriginX, y - gestureOriginY) > scaledTouchSlop) gestureMoved = true

                if (!scaleDetector.isInProgress) {
                    when {
                        inputMode == VncInputMode.DIRECT_TOUCH && event.pointerCount == 1 -> {
                            mapDirect(event.x, event.y)?.let { (rx, ry) ->
                                pointerX = rx
                                pointerY = ry
                                onInput(VncInputEvent.Pointer(rx, ry, if (dragging) 1 else 0))
                            }
                        }

                        event.pointerCount == 1 -> {
                            val moved = trackpad.move(
                                fingers = 1,
                                dx = dx,
                                dy = dy,
                                currentRemoteX = pointerX,
                                currentRemoteY = pointerY,
                                framebufferWidth = remoteWidth,
                                framebufferHeight = remoteHeight,
                                buttonsMask = if (dragging) 1 else 0,
                            )
                            pointerX = moved.x
                            pointerY = moved.y
                            onInput(VncInputEvent.Pointer(moved.x, moved.y, moved.buttonsMask))
                        }

                        event.pointerCount == 2 -> {
                            scrollAccumulatorX += dx
                            scrollAccumulatorY += dy
                            val sx = (scrollAccumulatorX / SCROLL_STEP_PX).toInt()
                            val sy = (scrollAccumulatorY / SCROLL_STEP_PX).toInt()
                            if (sx != 0 || sy != 0) {
                                onInput(VncInputEvent.Scroll(sx, sy, pointerX, pointerY))
                                scrollAccumulatorX -= sx * SCROLL_STEP_PX
                                scrollAccumulatorY -= sy * SCROLL_STEP_PX
                            }
                        }
                    }
                }
                lastX = x
                lastY = y
            }

            MotionEvent.ACTION_UP -> {
                if (dragging) {
                    dragging = false
                    onInput(VncInputEvent.Pointer(pointerX, pointerY, 0))
                } else if (!gestureMoved && !scaleDetector.isInProgress) {
                    when (maxPointers) {
                        1 -> click(1)
                        2 -> if (System.currentTimeMillis() - twoFingerStart < RIGHT_TAP_TIMEOUT_MS) click(4)
                        3 -> onKeyboardRequested()
                    }
                }
                maxPointers = 0
                scrollAccumulatorX = 0f
                scrollAccumulatorY = 0f
            }

            MotionEvent.ACTION_CANCEL -> {
                if (dragging) onInput(VncInputEvent.Pointer(pointerX, pointerY, 0))
                dragging = false
                maxPointers = 0
                scrollAccumulatorX = 0f
                scrollAccumulatorY = 0f
            }
        }
        return true
    }

    private fun click(mask: Int) {
        onInput(VncInputEvent.Pointer(pointerX, pointerY, mask))
        onInput(VncInputEvent.Pointer(pointerX, pointerY, 0))
    }

    private fun destinationRect(): RectF {
        val vw = width.toFloat().coerceAtLeast(1f)
        val vh = height.toFloat().coerceAtLeast(1f)
        val rw = remoteWidth.toFloat().coerceAtLeast(1f)
        val rh = remoteHeight.toFloat().coerceAtLeast(1f)
        return when (scaleMode) {
            VncScaleMode.STRETCH -> RectF(0f, 0f, vw, vh)
            VncScaleMode.ORIGINAL_SIZE -> {
                val w = rw * zoom
                val h = rh * zoom
                RectF((vw - w) / 2f, (vh - h) / 2f, (vw + w) / 2f, (vh + h) / 2f)
            }
            VncScaleMode.FIT_SCREEN -> {
                val scale = minOf(vw / rw, vh / rh) * zoom
                val w = rw * scale
                val h = rh * scale
                RectF((vw - w) / 2f, (vh - h) / 2f, (vw + w) / 2f, (vh + h) / 2f)
            }
            VncScaleMode.FILL_SCREEN -> {
                val scale = maxOf(vw / rw, vh / rh) * zoom
                val w = rw * scale
                val h = rh * scale
                RectF((vw - w) / 2f, (vh - h) / 2f, (vw + w) / 2f, (vh + h) / 2f)
            }
        }
    }

    private fun mapDirect(x: Float, y: Float): Pair<Int, Int>? {
        val rect = destinationRect()
        if (!rect.contains(x, y)) return null
        val rx = (((x - rect.left) / rect.width()) * remoteWidth).toInt().coerceIn(0, remoteWidth - 1)
        val ry = (((y - rect.top) / rect.height()) * remoteHeight).toInt().coerceIn(0, remoteHeight - 1)
        return rx to ry
    }

    private fun averageX(event: MotionEvent): Float =
        (0 until event.pointerCount).sumOf { event.getX(it).toDouble() }.toFloat() / event.pointerCount

    private fun averageY(event: MotionEvent): Float =
        (0 until event.pointerCount).sumOf { event.getY(it).toDouble() }.toFloat() / event.pointerCount

    override fun onDetachedFromWindow() {
        bitmap?.recycle()
        bitmap = null
        lastFrame = null
        super.onDetachedFromWindow()
    }

    companion object {
        private const val SCROLL_STEP_PX = 40f
        private const val RIGHT_TAP_TIMEOUT_MS = 500L
    }
}
