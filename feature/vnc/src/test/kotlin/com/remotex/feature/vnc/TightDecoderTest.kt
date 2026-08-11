package com.remotex.feature.vnc

import com.remotex.feature.vnc.protocol.RfbPixelFormat
import com.remotex.feature.vnc.protocol.TightDecoder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.util.zip.Deflater
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class TightDecoderTest {
    private val format = RfbPixelFormat.remoteXDefault()

    @Test
    fun tightFill_decodesRgb24SolidRectangle() {
        val framebuffer = IntArray(2)
        TightDecoder(format).use { decoder ->
            decoder.decodeRectangle(
                DataInputStream(ByteArrayInputStream(byteArrayOf(0x80.toByte(), 0x11, 0x22, 0x33))),
                framebuffer, 2, 1, 0, 0, 2, 1,
            )
        }
        assertArrayEquals(intArrayOf(0xff112233.toInt(), 0xff112233.toInt()), framebuffer)
    }

    @Test
    fun tightSmallCopy_usesThreeByteRgbSpecialCase() {
        val framebuffer = IntArray(2)
        val encoded = byteArrayOf(0x00, 0x11, 0x22, 0x33, 0xaa.toByte(), 0xbb.toByte(), 0xcc.toByte())
        TightDecoder(format).use { decoder ->
            decoder.decodeRectangle(DataInputStream(ByteArrayInputStream(encoded)), framebuffer, 2, 1, 0, 0, 2, 1)
        }
        assertArrayEquals(intArrayOf(0xff112233.toInt(), 0xffaabbcc.toInt()), framebuffer)
    }

    @Test
    fun tightPalette_expandsTwoColorRows() {
        val framebuffer = IntArray(2)
        val encoded = byteArrayOf(
            0x40, // explicit filter, stream 0
            0x01, // palette
            0x01, // two colours (stored as count - 1)
            0xff.toByte(), 0x00, 0x00, // red
            0x00, 0x00, 0xff.toByte(), // blue
            0x80.toByte(), // blue, red
        )
        TightDecoder(format).use { decoder ->
            decoder.decodeRectangle(DataInputStream(ByteArrayInputStream(encoded)), framebuffer, 2, 1, 0, 0, 2, 1)
        }
        assertArrayEquals(intArrayOf(0xff0000ff.toInt(), 0xffff0000.toInt()), framebuffer)
    }

    @Test
    fun tightGradient_noZlibReconstructsPixels() {
        val framebuffer = IntArray(4)
        val residuals = byteArrayOf(10,20,30,10,10,10,5,5,5,5,5,5)
        val encoded = ByteArrayOutputStream().apply {
            write(0xE0) // no-zlib + explicit filter
            write(0x02) // gradient filter
            write(12) // compact length
            write(residuals)
        }.toByteArray()
        TightDecoder(format).use { decoder ->
            decoder.decodeRectangle(DataInputStream(ByteArrayInputStream(encoded)), framebuffer, 2, 2, 0, 0, 2, 2)
        }
        assertArrayEquals(
            intArrayOf(
                0xff0a141e.toInt(), 0xff141e28.toInt(),
                0xff0f1923.toInt(), 0xff1e2832.toInt(),
            ),
            framebuffer,
        )
    }

    @Test
    fun tightZlib_copyInflatesExactDeclaredRectangle() {
        val raw = byteArrayOf(
            1,2,3, 4,5,6,
            7,8,9, 10,11,12,
        )
        val deflater = Deflater()
        val compressedBuffer = ByteArray(128)
        deflater.setInput(raw)
        val compressedSize = deflater.deflate(compressedBuffer, 0, compressedBuffer.size, Deflater.SYNC_FLUSH)
        deflater.end()
        require(compressedSize in 1..127)
        val encoded = ByteArrayOutputStream().apply {
            write(0x00)
            write(compressedSize)
            write(compressedBuffer, 0, compressedSize)
        }.toByteArray()
        val framebuffer = IntArray(4)
        TightDecoder(format).use { decoder ->
            decoder.decodeRectangle(DataInputStream(ByteArrayInputStream(encoded)), framebuffer, 2, 2, 0, 0, 2, 2)
        }
        assertArrayEquals(
            intArrayOf(0xff010203.toInt(), 0xff040506.toInt(), 0xff070809.toInt(), 0xff0a0b0c.toInt()),
            framebuffer,
        )
    }
}
