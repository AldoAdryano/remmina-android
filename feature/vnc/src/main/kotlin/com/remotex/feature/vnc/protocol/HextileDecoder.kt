package com.remotex.feature.vnc.protocol

import java.io.DataInputStream

class HextileDecoder(
    private val pixelFormat: RfbPixelFormat,
) {
    fun decodeRectangle(
        input: DataInputStream,
        framebuffer: IntArray,
        framebufferWidth: Int,
        framebufferHeight: Int,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) {
        require(x >= 0 && y >= 0 && x + width <= framebufferWidth && y + height <= framebufferHeight) {
            "Hextile rectangle is outside framebuffer"
        }
        var background = 0xff000000.toInt()
        var foreground = 0xff000000.toInt()
        var backgroundValid = false
        var foregroundValid = false
        var tileY = y
        while (tileY < y + height) {
            val tileHeight = minOf(TILE_SIZE, y + height - tileY)
            var tileX = x
            while (tileX < x + width) {
                val tileWidth = minOf(TILE_SIZE, x + width - tileX)
                val subencoding = input.readUnsignedByte()
                if ((subencoding and RAW) != 0) {
                    readRawTile(input, framebuffer, framebufferWidth, tileX, tileY, tileWidth, tileHeight)
                    backgroundValid = false
                    foregroundValid = false
                    tileX += tileWidth
                    continue
                }

                if ((subencoding and BACKGROUND_SPECIFIED) != 0) {
                    background = readPixel(input)
                    backgroundValid = true
                }
                require(backgroundValid) { "Hextile background was not specified after a raw/initial tile" }
                fillRect(framebuffer, framebufferWidth, tileX, tileY, tileWidth, tileHeight, background)

                val colouredSubrects = (subencoding and SUBRECTS_COLOURED) != 0
                if ((subencoding and FOREGROUND_SPECIFIED) != 0) {
                    require(!colouredSubrects) { "Hextile foreground cannot be specified with coloured subrectangles" }
                    foreground = readPixel(input)
                    foregroundValid = true
                }
                if ((subencoding and ANY_SUBRECTS) != 0) {
                    val count = input.readUnsignedByte()
                    if (!colouredSubrects) require(foregroundValid) { "Hextile foreground was not specified or carried" }
                    repeat(count) {
                        val color = if (colouredSubrects) readPixel(input) else foreground
                        val xy = input.readUnsignedByte()
                        val wh = input.readUnsignedByte()
                        val sx = tileX + (xy ushr 4)
                        val sy = tileY + (xy and 0x0f)
                        val sw = (wh ushr 4) + 1
                        val sh = (wh and 0x0f) + 1
                        require(sx + sw <= tileX + tileWidth && sy + sh <= tileY + tileHeight) {
                            "Hextile subrectangle is outside tile"
                        }
                        fillRect(framebuffer, framebufferWidth, sx, sy, sw, sh, color)
                    }
                }
                if (colouredSubrects) foregroundValid = false
                tileX += tileWidth
            }
            tileY += tileHeight
        }
    }

    private fun readRawTile(
        input: DataInputStream,
        framebuffer: IntArray,
        framebufferWidth: Int,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) {
        val row = ByteArray(width * pixelFormat.bytesPerPixel)
        repeat(height) { rowIndex ->
            input.readFully(row)
            val destination = (y + rowIndex) * framebufferWidth + x
            repeat(width) { column ->
                framebuffer[destination + column] = pixelFormat.decodePixel(row, column * pixelFormat.bytesPerPixel)
            }
        }
    }

    private fun readPixel(input: DataInputStream): Int {
        val bytes = ByteArray(pixelFormat.bytesPerPixel)
        input.readFully(bytes)
        return pixelFormat.decodePixel(bytes, 0)
    }

    private fun fillRect(
        framebuffer: IntArray,
        framebufferWidth: Int,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        color: Int,
    ) {
        repeat(height) { row ->
            java.util.Arrays.fill(
                framebuffer,
                (y + row) * framebufferWidth + x,
                (y + row) * framebufferWidth + x + width,
                color,
            )
        }
    }

    private companion object {
        const val TILE_SIZE = 16
        const val RAW = 1
        const val BACKGROUND_SPECIFIED = 2
        const val FOREGROUND_SPECIFIED = 4
        const val ANY_SUBRECTS = 8
        const val SUBRECTS_COLOURED = 16
    }
}
