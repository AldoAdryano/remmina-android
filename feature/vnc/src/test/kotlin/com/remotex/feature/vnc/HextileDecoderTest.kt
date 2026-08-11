package com.remotex.feature.vnc

import com.remotex.feature.vnc.protocol.HextileDecoder
import com.remotex.feature.vnc.protocol.RfbPixelFormat
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class HextileDecoderTest {
    @Test
    fun performancePixelFormat_decodesRgb565() {
        val format = RfbPixelFormat.remoteXPerformance()
        assertEquals(0xffff0000.toInt(), format.decodePixel(byteArrayOf(0x00, 0xF8.toByte()), 0))
        assertEquals(0xff00ff00.toInt(), format.decodePixel(byteArrayOf(0xE0.toByte(), 0x07), 0))
        assertEquals(0xff0000ff.toInt(), format.decodePixel(byteArrayOf(0x1F, 0x00), 0))
    }

    @Test
    fun hextile_decodesBackgroundAndColouredSubrect() {
        val encoded = byteArrayOf(
            (2 or 8 or 16).toByte(),
            0x00, 0xF8.toByte(),
            0x01,
            0x1F, 0x00,
            0x11,
            0x00,
        )
        val framebuffer = IntArray(4)
        HextileDecoder(RfbPixelFormat.remoteXPerformance()).decodeRectangle(
            input = DataInputStream(ByteArrayInputStream(encoded)),
            framebuffer = framebuffer,
            framebufferWidth = 2,
            framebufferHeight = 2,
            x = 0,
            y = 0,
            width = 2,
            height = 2,
        )
        assertArrayEquals(
            intArrayOf(
                0xffff0000.toInt(), 0xffff0000.toInt(),
                0xffff0000.toInt(), 0xff0000ff.toInt(),
            ),
            framebuffer,
        )
    }
}
