package com.remotex.feature.vnc.protocol

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.DataInputStream
import java.util.zip.DataFormatException
import java.util.zip.Inflater

/**
 * Bounded decoder for RFB Tight encoding (encoding 7).
 *
 * The server controls every length handled here, so rectangle dimensions,
 * compressed sizes, decompressed sizes, palette indexes and JPEG dimensions
 * are validated before data is copied into the framebuffer.
 */
class TightDecoder(
    private val pixelFormat: RfbPixelFormat,
) : AutoCloseable {
    private val inflaters = arrayOfNulls<Inflater>(4)

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
        validateRectangle(framebuffer, framebufferWidth, framebufferHeight, x, y, width, height)

        val control = input.readUnsignedByte()
        repeat(4) { streamId ->
            if (control and (1 shl streamId) != 0) resetInflater(streamId)
        }
        var subencoding = control ushr 4
        var noZlib = false
        if (subencoding and TIGHT_NO_ZLIB == TIGHT_NO_ZLIB) {
            subencoding = subencoding and TIGHT_NO_ZLIB.inv()
            noZlib = true
        }

        when (subencoding) {
            TIGHT_FILL -> {
                val color = readTightColor(input)
                fill(framebuffer, framebufferWidth, x, y, width, height, color)
                return
            }

            TIGHT_JPEG -> {
                decodeJpeg(input, framebuffer, framebufferWidth, x, y, width, height)
                return
            }
        }
        require(subencoding <= TIGHT_MAX_SUBENCODING) { "Unsupported Tight subencoding: $subencoding" }

        val filter = if (subencoding and TIGHT_EXPLICIT_FILTER != 0) {
            when (val filterId = input.readUnsignedByte()) {
                FILTER_COPY -> Filter.Copy
                FILTER_PALETTE -> readPalette(input)
                FILTER_GRADIENT -> Filter.Gradient
                else -> error("Unsupported Tight filter: $filterId")
            }
        } else {
            Filter.Copy
        }

        val bitsPerPixel = when (filter) {
            is Filter.Palette -> if (filter.colors.size == 2) 1 else 8
            Filter.Copy, Filter.Gradient -> tightBitsPerPixel()
        }
        if (filter == Filter.Gradient) {
            require(pixelFormat.bitsPerPixel == 16 || pixelFormat.bitsPerPixel == 32) {
                "Tight gradient requires 16-bit or 32-bit pixels"
            }
        }

        val rowBytes = checkedRowBytes(width, bitsPerPixel)
        val expectedBytes = checkedPayloadBytes(rowBytes, height)
        val payload = when {
            expectedBytes < MIN_TO_COMPRESS -> ByteArray(expectedBytes).also(input::readFully)
            else -> {
                val encodedLength = TightCodec.readCompactLength(input, MAX_COMPRESSED_BYTES)
                require(encodedLength > 0) { "Invalid Tight payload length" }
                if (noZlib) {
                    require(encodedLength == expectedBytes) {
                        "Tight uncompressed payload size mismatch: $encodedLength != $expectedBytes"
                    }
                    ByteArray(encodedLength).also(input::readFully)
                } else {
                    inflateExact(
                        streamId = subencoding and 0x03,
                        compressed = ByteArray(encodedLength).also(input::readFully),
                        expectedBytes = expectedBytes,
                    )
                }
            }
        }

        when (filter) {
            Filter.Copy -> applyCopy(payload, framebuffer, framebufferWidth, x, y, width, height)
            is Filter.Palette -> applyPalette(payload, filter.colors, framebuffer, framebufferWidth, x, y, width, height)
            Filter.Gradient -> applyGradient(payload, framebuffer, framebufferWidth, x, y, width, height)
        }
    }

    private fun validateRectangle(
        framebuffer: IntArray,
        framebufferWidth: Int,
        framebufferHeight: Int,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) {
        require(width in 1..TightCodec.MAX_TIGHT_WIDTH && height > 0) { "Invalid Tight rectangle size: ${width}x$height" }
        require(x >= 0 && y >= 0 && x.toLong() + width <= framebufferWidth && y.toLong() + height <= framebufferHeight) {
            "Tight rectangle is outside framebuffer"
        }
        require(framebuffer.size == Math.multiplyExact(framebufferWidth, framebufferHeight)) { "Invalid framebuffer storage" }
        require(width.toLong() * height <= MAX_RECT_PIXELS) { "Tight rectangle exceeds pixel safety limit" }
    }

    private fun readPalette(input: DataInputStream): Filter.Palette {
        val count = input.readUnsignedByte() + 1
        require(count in 2..256) { "Invalid Tight palette size: $count" }
        return Filter.Palette(IntArray(count) { readTightColor(input) })
    }

    private fun readTightColor(input: DataInputStream): Int {
        return if (usesRgb24()) {
            val r = input.readUnsignedByte()
            val g = input.readUnsignedByte()
            val b = input.readUnsignedByte()
            (0xff shl 24) or (r shl 16) or (g shl 8) or b
        } else {
            val bytes = ByteArray(pixelFormat.bytesPerPixel)
            input.readFully(bytes)
            pixelFormat.decodePixel(bytes, 0)
        }
    }

    private fun tightBitsPerPixel(): Int = if (usesRgb24()) 24 else pixelFormat.bitsPerPixel

    private fun usesRgb24(): Boolean =
        pixelFormat.bitsPerPixel == 32 && pixelFormat.depth == 24 &&
            pixelFormat.redMax == 255 && pixelFormat.greenMax == 255 && pixelFormat.blueMax == 255

    private fun checkedRowBytes(width: Int, bitsPerPixel: Int): Int {
        val bits = Math.multiplyExact(width.toLong(), bitsPerPixel.toLong())
        val bytes = (bits + 7L) / 8L
        require(bytes in 1..MAX_DECOMPRESSED_BYTES.toLong()) { "Tight row is too large" }
        return bytes.toInt()
    }

    private fun checkedPayloadBytes(rowBytes: Int, height: Int): Int {
        val bytes = Math.multiplyExact(rowBytes.toLong(), height.toLong())
        require(bytes in 1..MAX_DECOMPRESSED_BYTES.toLong()) { "Tight payload is too large" }
        return bytes.toInt()
    }

    private fun inflateExact(streamId: Int, compressed: ByteArray, expectedBytes: Int): ByteArray {
        require(streamId in 0..3)
        require(compressed.size <= MAX_COMPRESSED_BYTES)
        val inflater = inflaters[streamId] ?: Inflater().also { inflaters[streamId] = it }
        inflater.setInput(compressed)
        val output = ByteArray(expectedBytes)
        var offset = 0
        val overflowProbe = ByteArray(1)
        try {
            while (true) {
                val count = if (offset < output.size) {
                    inflater.inflate(output, offset, output.size - offset)
                } else {
                    inflater.inflate(overflowProbe)
                }
                if (count > 0) {
                    if (offset >= output.size) error("Tight zlib expanded beyond declared rectangle")
                    offset += count
                    continue
                }
                when {
                    inflater.needsDictionary() -> error("Tight zlib dictionary is unsupported")
                    inflater.needsInput() -> break
                    inflater.finished() -> break
                    else -> error("Tight zlib decoder made no progress")
                }
            }
        } catch (e: DataFormatException) {
            throw IllegalArgumentException("Invalid Tight zlib stream", e)
        }
        require(offset == expectedBytes) { "Tight zlib output size mismatch: $offset != $expectedBytes" }
        require(inflater.remaining == 0) { "Tight zlib left unread compressed bytes" }
        return output
    }

    private fun applyCopy(
        payload: ByteArray,
        framebuffer: IntArray,
        framebufferWidth: Int,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) {
        var source = 0
        repeat(height) { row ->
            var destination = (y + row) * framebufferWidth + x
            repeat(width) {
                framebuffer[destination++] = if (usesRgb24()) {
                    val r = payload[source++].toInt() and 0xff
                    val g = payload[source++].toInt() and 0xff
                    val b = payload[source++].toInt() and 0xff
                    (0xff shl 24) or (r shl 16) or (g shl 8) or b
                } else {
                    pixelFormat.decodePixel(payload, source).also { source += pixelFormat.bytesPerPixel }
                }
            }
        }
        require(source == payload.size) { "Tight copy payload mismatch" }
    }

    private fun applyPalette(
        payload: ByteArray,
        palette: IntArray,
        framebuffer: IntArray,
        framebufferWidth: Int,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) {
        val decoded = TightCodec.expandPalette(payload, width, height, palette)
        repeat(height) { row ->
            decoded.copyInto(
                destination = framebuffer,
                destinationOffset = (y + row) * framebufferWidth + x,
                startIndex = row * width,
                endIndex = (row + 1) * width,
            )
        }
    }

    private fun applyGradient(
        payload: ByteArray,
        framebuffer: IntArray,
        framebufferWidth: Int,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) {
        if (usesRgb24()) {
            val decoded = TightCodec.reconstructGradient24(payload, width, height)
            repeat(height) { row ->
                decoded.copyInto(framebuffer, (y + row) * framebufferWidth + x, row * width, (row + 1) * width)
            }
            return
        }

        val previous = IntArray(width * 3)
        val current = IntArray(width * 3)
        val max = intArrayOf(pixelFormat.redMax, pixelFormat.greenMax, pixelFormat.blueMax)
        val shift = intArrayOf(pixelFormat.redShift, pixelFormat.greenShift, pixelFormat.blueShift)
        var source = 0
        repeat(height) { row ->
            repeat(width) { column ->
                val rawResidual = readRawPixel(payload, source)
                source += pixelFormat.bytesPerPixel
                val base = column * 3
                var reconstructedRaw = 0
                repeat(3) { component ->
                    val residual = (rawResidual ushr shift[component]) and max[component]
                    val estimate = if (column == 0) {
                        previous[component]
                    } else {
                        (previous[base + component] + current[base - 3 + component] - previous[base - 3 + component])
                            .coerceIn(0, max[component])
                    }
                    val actual = (residual + estimate) and max[component]
                    current[base + component] = actual
                    reconstructedRaw = reconstructedRaw or (actual shl shift[component])
                }
                framebuffer[(y + row) * framebufferWidth + x + column] = decodeRawPixel(reconstructedRaw)
            }
            current.copyInto(previous)
        }
        require(source == payload.size) { "Tight gradient payload mismatch" }
    }

    private fun readRawPixel(bytes: ByteArray, offset: Int): Int = when (pixelFormat.bitsPerPixel) {
        16 -> if (pixelFormat.bigEndian) {
            ((bytes[offset].toInt() and 0xff) shl 8) or (bytes[offset + 1].toInt() and 0xff)
        } else {
            (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)
        }
        32 -> if (pixelFormat.bigEndian) {
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
        else -> error("Unsupported Tight pixel format")
    }

    private fun decodeRawPixel(raw: Int): Int {
        fun scale(value: Int, maximum: Int): Int = if (maximum == 255) value else (value * 255) / maximum.coerceAtLeast(1)
        val red = scale((raw ushr pixelFormat.redShift) and pixelFormat.redMax, pixelFormat.redMax)
        val green = scale((raw ushr pixelFormat.greenShift) and pixelFormat.greenMax, pixelFormat.greenMax)
        val blue = scale((raw ushr pixelFormat.blueShift) and pixelFormat.blueMax, pixelFormat.blueMax)
        return (0xff shl 24) or (red shl 16) or (green shl 8) or blue
    }

    private fun decodeJpeg(
        input: DataInputStream,
        framebuffer: IntArray,
        framebufferWidth: Int,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) {
        val length = TightCodec.readCompactLength(input, MAX_JPEG_BYTES)
        require(length > 0) { "Invalid Tight JPEG length" }
        val jpeg = ByteArray(length)
        input.readFully(jpeg)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
        require(bounds.outWidth == width && bounds.outHeight == height) {
            "Tight JPEG dimensions ${bounds.outWidth}x${bounds.outHeight} do not match ${width}x$height"
        }
        require(bounds.outWidth.toLong() * bounds.outHeight <= MAX_RECT_PIXELS) { "Tight JPEG exceeds pixel safety limit" }
        val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, options)
            ?: error("Invalid Tight JPEG payload")
        try {
            require(bitmap.width == width && bitmap.height == height) {
                "Tight JPEG decode dimensions changed unexpectedly"
            }
            val pixels = IntArray(Math.multiplyExact(width, height))
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            repeat(height) { row ->
                pixels.copyInto(framebuffer, (y + row) * framebufferWidth + x, row * width, (row + 1) * width)
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun fill(framebuffer: IntArray, framebufferWidth: Int, x: Int, y: Int, width: Int, height: Int, color: Int) {
        repeat(height) { row ->
            java.util.Arrays.fill(framebuffer, (y + row) * framebufferWidth + x, (y + row) * framebufferWidth + x + width, color)
        }
    }

    private fun resetInflater(streamId: Int) {
        inflaters[streamId]?.end()
        inflaters[streamId] = null
    }

    override fun close() {
        inflaters.forEach { it?.end() }
        repeat(inflaters.size) { inflaters[it] = null }
    }

    private sealed interface Filter {
        data object Copy : Filter
        data object Gradient : Filter
        data class Palette(val colors: IntArray) : Filter
    }

    companion object {
        private const val TIGHT_EXPLICIT_FILTER = 0x04
        private const val TIGHT_FILL = 0x08
        private const val TIGHT_JPEG = 0x09
        private const val TIGHT_NO_ZLIB = 0x0A
        private const val TIGHT_MAX_SUBENCODING = 0x0A
        private const val FILTER_COPY = 0
        private const val FILTER_PALETTE = 1
        private const val FILTER_GRADIENT = 2
        private const val MIN_TO_COMPRESS = 12

        const val MAX_COMPRESSED_BYTES = 32 * 1024 * 1024
        const val MAX_JPEG_BYTES = 16 * 1024 * 1024
        private const val MAX_DECOMPRESSED_BYTES = 64 * 1024 * 1024
        private const val MAX_RECT_PIXELS = 16L * 1024L * 1024L
    }
}
