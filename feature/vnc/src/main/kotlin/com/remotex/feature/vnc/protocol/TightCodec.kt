package com.remotex.feature.vnc.protocol

import java.io.DataInputStream

/** Pure helpers for the Tight RFB encoding. No Android dependencies. */
object TightCodec {
    fun readCompactLength(input: DataInputStream, maxValue: Int = Int.MAX_VALUE): Int {
        val first = input.readUnsignedByte()
        var value = first and 0x7f
        if (first and 0x80 != 0) {
            val second = input.readUnsignedByte()
            value = value or ((second and 0x7f) shl 7)
            if (second and 0x80 != 0) {
                val third = input.readUnsignedByte()
                value = value or (third shl 14)
            }
        }
        require(value in 0..maxValue) { "Tight compact length exceeds limit: $value" }
        return value
    }

    fun expandPalette(data: ByteArray, width: Int, height: Int, palette: IntArray): IntArray {
        require(width >= 0 && height >= 0)
        require(palette.size in 2..256) { "Invalid Tight palette size: ${palette.size}" }
        val pixels = Math.multiplyExact(width, height)
        val result = IntArray(pixels)
        if (palette.size == 2) {
            val rowBytes = (width + 7) / 8
            require(data.size == Math.multiplyExact(rowBytes, height)) { "Invalid Tight 1-bit palette payload" }
            var dst = 0
            repeat(height) { row ->
                repeat(width) { x ->
                    val packed = data[row * rowBytes + x / 8].toInt() and 0xff
                    result[dst++] = palette[(packed ushr (7 - (x and 7))) and 1]
                }
            }
        } else {
            require(data.size == pixels) { "Invalid Tight palette payload" }
            data.forEachIndexed { index, value ->
                val paletteIndex = value.toInt() and 0xff
                require(paletteIndex < palette.size) { "Tight palette index out of bounds: $paletteIndex" }
                result[index] = palette[paletteIndex]
            }
        }
        return result
    }

    fun reconstructGradient24(residuals: ByteArray, width: Int, height: Int): IntArray {
        require(width in 1..MAX_TIGHT_WIDTH && height > 0)
        val pixels = Math.multiplyExact(width, height)
        require(residuals.size == Math.multiplyExact(pixels, 3)) { "Invalid Tight gradient payload" }
        val result = IntArray(pixels)
        val previous = IntArray(width * 3)
        val current = IntArray(width * 3)
        var source = 0
        repeat(height) { y ->
            repeat(width) { x ->
                val base = x * 3
                repeat(3) { component ->
                    val estimate = if (x == 0) {
                        previous[component]
                    } else {
                        (previous[base + component] + current[base - 3 + component] - previous[base - 3 + component])
                            .coerceIn(0, 255)
                    }
                    current[base + component] = (estimate + (residuals[source++].toInt() and 0xff)) and 0xff
                }
                result[y * width + x] =
                    (0xff shl 24) or (current[base] shl 16) or (current[base + 1] shl 8) or current[base + 2]
            }
            current.copyInto(previous)
        }
        return result
    }

    const val MAX_TIGHT_WIDTH = 2048
}
