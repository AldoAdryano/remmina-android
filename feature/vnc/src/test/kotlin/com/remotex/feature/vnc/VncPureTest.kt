package com.remotex.feature.vnc

import com.remotex.feature.vnc.input.TrackpadGestureInterpreter
import com.remotex.feature.vnc.input.TrackpadTouchGuard
import com.remotex.feature.vnc.input.TrackpadResult
import com.remotex.feature.vnc.protocol.RfbAuth
import com.remotex.feature.vnc.protocol.RfbPixelFormat
import com.remotex.feature.vnc.session.ReconnectPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VncCoreTest {
    @Test
    fun vncDes_matchesKnownVector() {
        val challenge = ByteArray(16) { it.toByte() }
        val response = RfbAuth.challengeResponse("password".toCharArray(), challenge)
        assertEquals("b866924125c8eebb9debc1db61c538e2", response.toHex())
    }

    @Test
    fun reconnectPolicy_stopsAfterThreeRetries() {
        val policy = ReconnectPolicy()
        assertEquals(1_000L, policy.delayMillis(1))
        assertEquals(2_000L, policy.delayMillis(2))
        assertEquals(4_000L, policy.delayMillis(3))
        assertNull(policy.delayMillis(4))
    }

    @Test
    fun trackpad_mapsMovementAndRightClick() {
        val trackpad = TrackpadGestureInterpreter(pointerSpeed = 1f, acceleration = 0f)
        assertEquals(TrackpadResult.Pointer(125, 90, 0), trackpad.move(1, 25f, -10f, 100, 100, 1920, 1080))
        assertEquals(TrackpadResult.Pointer(0, 0, 0), trackpad.move(1, -500f, -500f, 10, 10, 1920, 1080))
        assertEquals(listOf(TrackpadResult.PointerButton(1), TrackpadResult.PointerButton(0)), trackpad.leftTap())
        assertEquals(listOf(TrackpadResult.PointerButton(4), TrackpadResult.PointerButton(0)), trackpad.rightTap())
    }

    @Test
    fun trackpad_acceleratesLongSwipesButKeepsShortMovesPrecise() {
        val trackpad = TrackpadGestureInterpreter(
            pointerSpeed = 1f,
            acceleration = 0.5f,
            accelerationDistance = 40f,
        )
        assertEquals(TrackpadResult.Pointer(111, 100, 0), trackpad.move(1, 10f, 0f, 100, 100, 1920, 1080))
        assertEquals(TrackpadResult.Pointer(160, 100, 0), trackpad.move(1, 40f, 0f, 100, 100, 1920, 1080))
    }


    @Test
    fun trackpad_microJitterDoesNotDriftTowardTopLeft() {
        val trackpad = TrackpadGestureInterpreter(pointerSpeed = 1f, acceleration = 0f)
        var x = 500
        var y = 500
        repeat(40) {
            trackpad.move(1, -0.2f, -0.2f, x, y, 1920, 1080).also { x = it.x; y = it.y }
            trackpad.move(1, 0.2f, 0.2f, x, y, 1920, 1080).also { x = it.x; y = it.y }
        }
        assertEquals(500, x)
        assertEquals(500, y)
    }

    @Test
    fun trackpad_scrollUsesNaturalDirection() {
        val trackpad = TrackpadGestureInterpreter(pointerSpeed = 1f, acceleration = 0f)
        assertEquals(TrackpadResult.Scroll(0, 1), trackpad.scroll(0f, -40f, 40f))
        trackpad.resetGesture()
        assertEquals(TrackpadResult.Scroll(0, -1), trackpad.scroll(0f, 40f, 40f))
    }

    @Test
    fun trackpad_doesNotResumePointerMovementMidMultitouchGesture() {
        val guard = TrackpadTouchGuard()
        guard.beginGesture()
        assertTrue(guard.canMovePointer(1))
        guard.observePointerCount(2)
        assertTrue(!guard.canMovePointer(1))
        guard.endGesture()
        guard.beginGesture()
        assertTrue(guard.canMovePointer(1))
    }

    @Test
    fun defaultPixelFormat_decodesBgra() {
        val format = RfbPixelFormat.remoteXDefault()
        assertTrue(format.decodePixel(byteArrayOf(0x33, 0x22, 0x11, 0x00), 0) == 0xFF112233.toInt())
    }
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }
