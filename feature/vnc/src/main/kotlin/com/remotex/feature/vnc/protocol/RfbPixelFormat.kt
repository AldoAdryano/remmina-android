package com.remotex.feature.vnc.protocol

data class RfbPixelFormat(
    val bitsPerPixel: Int,
    val depth: Int,
    val bigEndian: Boolean,
    val trueColor: Boolean,
    val redMax: Int,
    val greenMax: Int,
    val blueMax: Int,
    val redShift: Int,
    val greenShift: Int,
    val blueShift: Int,
) {
    val bytesPerPixel: Int get() = bitsPerPixel / 8

    fun decodePixel(bytes: ByteArray, offset: Int): Int {
        require(trueColor) { "RemoteX V1 only renders true-color RFB pixel formats" }
        require(bitsPerPixel == 32) { "RemoteX V1 requests 32-bit RFB pixels" }
        val raw = if (bigEndian) {
            ((bytes[offset].toInt() and 0xff) shl 24) or
                ((bytes[offset + 1].toInt() and 0xff) shl 16) or
                ((bytes[offset + 2].toInt() and 0xff) shl 8) or
                (bytes[offset + 3].toInt() and 0xff)
        } else {
            (bytes[offset].toInt() and 0xff) or
                ((bytes[offset + 1].toInt() and 0xff) shl 8) or
                ((bytes[offset + 2].toInt() and 0xff) shl 16) or
                ((bytes[offset + 3].toInt() and 0xff) shl 24)
        }
        val red = scale((raw ushr redShift) and redMax, redMax)
        val green = scale((raw ushr greenShift) and greenMax, greenMax)
        val blue = scale((raw ushr blueShift) and blueMax, blueMax)
        return (0xff shl 24) or (red shl 16) or (green shl 8) or blue
    }

    private fun scale(value: Int, max: Int): Int = if (max == 255) value else (value * 255) / max.coerceAtLeast(1)

    companion object {
        fun remoteXDefault() = RfbPixelFormat(
            bitsPerPixel = 32,
            depth = 24,
            bigEndian = false,
            trueColor = true,
            redMax = 255,
            greenMax = 255,
            blueMax = 255,
            redShift = 16,
            greenShift = 8,
            blueShift = 0,
        )
    }
}
