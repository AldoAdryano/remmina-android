package com.remotex.feature.vnc

import com.remotex.feature.vnc.protocol.TightCodec
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class TightCodecTest {
    @Test
    fun compactLength_decodesOneTwoAndThreeByteForms() {
        fun decode(vararg values: Int): Int = TightCodec.readCompactLength(
            DataInputStream(ByteArrayInputStream(values.map(Int::toByte).toByteArray())),
        )
        assertEquals(0x7f, decode(0x7f))
        assertEquals(0x80, decode(0x80, 0x01))
        assertEquals(0x4000, decode(0x80, 0x80, 0x01))
    }

    @Test
    fun paletteTwoColor_expandsMostSignificantBitFirst() {
        val output = TightCodec.expandPalette(
            data = byteArrayOf(0b10100000.toByte()),
            width = 3,
            height = 1,
            palette = intArrayOf(0xff000000.toInt(), 0xffffffff.toInt()),
        )
        assertArrayEquals(
            intArrayOf(0xffffffff.toInt(), 0xff000000.toInt(), 0xffffffff.toInt()),
            output,
        )
    }

    @Test
    fun gradient24_reconstructsResidualsWithoutOverflow() {
        val residuals = byteArrayOf(
            10, 20, 30, 10, 10, 10,
            5, 5, 5, 5, 5, 5,
        )
        val output = TightCodec.reconstructGradient24(residuals, width = 2, height = 2)
        assertArrayEquals(
            intArrayOf(
                0xff0a141e.toInt(), 0xff141e28.toInt(),
                0xff0f1923.toInt(), 0xff1e2832.toInt(),
            ),
            output,
        )
    }
}
