package com.remotex.feature.vnc

import com.remotex.feature.vnc.input.TrackpadGestureInterpreter
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
        val trackpad = TrackpadGestureInterpreter(pointerSpeed = 1f)
        assertEquals(TrackpadResult.Pointer(125, 90, 0), trackpad.move(1, 25f, -10f, 100, 100, 1920, 1080))
        assertEquals(TrackpadResult.Pointer(0, 0, 0), trackpad.move(1, -500f, -500f, 10, 10, 1920, 1080))
        assertEquals(listOf(TrackpadResult.PointerButton(1), TrackpadResult.PointerButton(0)), trackpad.leftTap())
        assertEquals(listOf(TrackpadResult.PointerButton(4), TrackpadResult.PointerButton(0)), trackpad.rightTap())
    }

    @Test
    fun defaultPixelFormat_decodesBgra() {
        val format = RfbPixelFormat.remoteXDefault()
        assertTrue(format.decodePixel(byteArrayOf(0x33, 0x22, 0x11, 0x00), 0) == 0xFF112233.toInt())
    }
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }
