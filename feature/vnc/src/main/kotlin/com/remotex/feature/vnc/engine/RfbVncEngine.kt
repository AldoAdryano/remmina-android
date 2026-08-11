package com.remotex.feature.vnc.engine

import com.remotex.feature.vnc.domain.VncConnectionSpec
import com.remotex.feature.vnc.domain.VncFrame
import com.remotex.feature.vnc.domain.VncInputEvent
import com.remotex.feature.vnc.domain.VncSessionState
import com.remotex.feature.vnc.input.TrackpadGestureInterpreter
import com.remotex.feature.vnc.protocol.HextileDecoder
import com.remotex.feature.vnc.protocol.RfbAuth
import com.remotex.feature.vnc.protocol.RfbPixelFormat
import com.remotex.feature.vnc.protocol.readLengthPrefixedText
import com.remotex.feature.vnc.protocol.readS32
import com.remotex.feature.vnc.protocol.readU16
import com.remotex.feature.vnc.protocol.readU32
import com.remotex.feature.vnc.protocol.readU8
import com.remotex.feature.vnc.protocol.writeU16
import com.remotex.feature.vnc.protocol.writeU32
import com.remotex.feature.vnc.quality.AdaptiveQualityController
import com.remotex.feature.vnc.quality.VncPerformanceStats
import com.remotex.feature.vnc.quality.VncQualityMode
import com.remotex.feature.vnc.quality.profileFor
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RfbVncEngine(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val connectTimeoutMillis: Int = 10_000,
) : VncEngine {
    private val _state = MutableStateFlow<VncSessionState>(VncSessionState.Idle)
    override val state: StateFlow<VncSessionState> = _state.asStateFlow()

    private val _frames = MutableSharedFlow<VncFrame>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val frames: Flow<VncFrame> = _frames.asSharedFlow()

    private val _remoteClipboard = MutableSharedFlow<String>(extraBufferCapacity = 2)
    override val remoteClipboard: Flow<String> = _remoteClipboard.asSharedFlow()

    private val _performanceStats = MutableStateFlow(VncPerformanceStats())
    override val performanceStats: StateFlow<VncPerformanceStats> = _performanceStats.asStateFlow()

    private var socket: Socket? = null
    private var input: DataInputStream? = null
    private var output: DataOutputStream? = null
    private var readerJob: Job? = null
    private val writerLock = Any()

    @Volatile private var framebufferWidth: Int = 0
    @Volatile private var framebufferHeight: Int = 0
    private var framebuffer = IntArray(0)
    @Volatile private var selectedQuality = VncQualityMode.BALANCED
    @Volatile private var requestedEffectiveQuality = VncQualityMode.BALANCED
    private var appliedQuality = VncQualityMode.BALANCED
    private var pixelFormat = RfbPixelFormat.remoteXDefault()
    private var hextileDecoder = HextileDecoder(pixelFormat)
    private val adaptiveQuality = AdaptiveQualityController()
    private var fpsWindowStartedNanos = System.nanoTime()
    private var changedFramesInWindow = 0

    override suspend fun connect(spec: VncConnectionSpec) = coroutineScope {
        disconnectInternal(markClosed = false)
        if (selectedQuality == VncQualityMode.AUTO) {
            adaptiveQuality.reset()
            requestedEffectiveQuality = VncQualityMode.BALANCED
        }
        _state.value = VncSessionState.Connecting

        try {
            withContext(ioDispatcher) {
                val connectedSocket = Socket()
                connectedSocket.tcpNoDelay = true
                connectedSocket.keepAlive = true
                connectedSocket.connect(InetSocketAddress(spec.host, spec.port), connectTimeoutMillis)
                socket = connectedSocket
                input = DataInputStream(BufferedInputStream(connectedSocket.getInputStream(), 64 * 1024))
                output = DataOutputStream(BufferedOutputStream(connectedSocket.getOutputStream(), 64 * 1024))
                performHandshake(spec)
            }

            readerJob = launch(ioDispatcher + SupervisorJob()) { readServerLoop() }
        } catch (t: Throwable) {
            disconnectInternal(markClosed = false)
            if (t is CancellationException) throw t
            _state.value = VncSessionState.Failed(sanitizeError(t), retryable = t !is SecurityException)
        }
    }

    override suspend fun send(event: VncInputEvent) = withContext(ioDispatcher) {
        val out = output ?: return@withContext
        synchronized(writerLock) {
            when (event) {
                is VncInputEvent.Pointer -> sendPointer(out, event.x, event.y, event.buttonsMask)
                is VncInputEvent.Scroll -> sendScroll(out, event)
                is VncInputEvent.Key -> {
                    out.writeByte(4)
                    out.writeByte(if (event.down) 1 else 0)
                    out.writeShort(0)
                    out.writeU32(event.keysym.toLong())
                    out.flush()
                }
                is VncInputEvent.Clipboard -> {
                    val bytes = event.text.toByteArray(StandardCharsets.ISO_8859_1)
                    out.writeByte(6)
                    out.write(byteArrayOf(0, 0, 0))
                    out.writeU32(bytes.size.toLong())
                    out.write(bytes)
                    out.flush()
                }
            }
        }
    }

    override suspend fun setQualityMode(mode: VncQualityMode) {
        selectedQuality = mode
        if (mode == VncQualityMode.AUTO) {
            adaptiveQuality.reset()
            requestedEffectiveQuality = VncQualityMode.BALANCED
        } else {
            requestedEffectiveQuality = mode
        }
    }

    override suspend fun disconnect() {
        disconnectInternal(markClosed = true)
    }

    private suspend fun disconnectInternal(markClosed: Boolean) {
        val job = readerJob
        readerJob = null
        if (job != null && job != kotlinx.coroutines.currentCoroutineContext()[Job]) {
            job.cancelAndJoin()
        }
        withContext(ioDispatcher) {
            runCatching { input?.close() }
            runCatching { output?.close() }
            runCatching { socket?.close() }
        }
        input = null
        output = null
        socket = null
        if (markClosed) _state.value = VncSessionState.Closed
    }

    private fun performHandshake(spec: VncConnectionSpec) {
        val input = requireNotNull(input)
        val output = requireNotNull(output)

        val versionBytes = ByteArray(12)
        input.readFully(versionBytes)
        val serverVersion = versionBytes.toString(StandardCharsets.US_ASCII)
        val protocol = selectProtocol(serverVersion)
        output.write(protocol.banner.toByteArray(StandardCharsets.US_ASCII))
        output.flush()

        if (protocol.minor >= 7) {
            val count = input.readU8()
            if (count == 0) throw SecurityException(input.readLengthPrefixedText())
            val types = IntArray(count) { input.readU8() }.toSet()
            val selected = when {
                spec.password != null && SECURITY_VNC in types -> SECURITY_VNC
                spec.password == null && SECURITY_NONE in types -> SECURITY_NONE
                spec.password != null && SECURITY_NONE in types -> SECURITY_NONE
                else -> throw SecurityException("Server does not offer a supported VNC security type")
            }
            output.writeByte(selected)
            output.flush()
            if (selected == SECURITY_VNC) authenticateVnc(input, output, requireNotNull(spec.password), protocol.minor)
            else if (protocol.minor >= 8) readSecurityResult(input, protocol.minor)
        } else {
            when (val securityType = input.readInt()) {
                0 -> throw SecurityException(input.readLengthPrefixedText())
                SECURITY_NONE -> Unit
                SECURITY_VNC -> authenticateVnc(input, output, spec.password ?: throw SecurityException("VNC password required"), protocol.minor)
                else -> throw SecurityException("Unsupported RFB 3.3 security type: $securityType")
            }
        }

        output.writeByte(if (spec.shared) 1 else 0)
        output.flush()

        val width = input.readU16()
        val height = input.readU16()
        readServerPixelFormat(input)
        val desktopName = input.readLengthPrefixedText(maxBytes = 4 * 1024 * 1024)
        setFramebufferSize(width, height)

        resetPerformanceWindow()
        applyProfileLocally(requestedEffectiveQuality)
        sendSetPixelFormat(output, pixelFormat)
        sendSetEncodings(output, requestedEffectiveQuality.profileFor().preferRaw)
        requestFramebufferUpdate(output, incremental = false)
        _state.value = VncSessionState.Connected(width, height, desktopName)
    }

    private fun selectProtocol(version: String): ProtocolVersion {
        require(version.startsWith("RFB ") && version.endsWith("\n")) { "Invalid RFB version banner" }
        val major = version.substring(4, 7).toIntOrNull() ?: error("Invalid RFB major version")
        val minor = version.substring(8, 11).toIntOrNull() ?: error("Invalid RFB minor version")
        require(major == 3) { "Unsupported RFB major version: $major" }
        return when {
            minor >= 8 -> ProtocolVersion(3, 8)
            minor >= 7 -> ProtocolVersion(3, 7)
            else -> ProtocolVersion(3, 3)
        }
    }

    private fun authenticateVnc(input: DataInputStream, output: DataOutputStream, password: CharArray, minor: Int) {
        val challenge = ByteArray(16)
        input.readFully(challenge)
        val response = RfbAuth.challengeResponse(password, challenge)
        try {
            output.write(response)
            output.flush()
        } finally {
            challenge.fill(0)
            response.fill(0)
        }
        readSecurityResult(input, minor)
    }

    private fun readSecurityResult(input: DataInputStream, minor: Int) {
        when (val status = input.readInt()) {
            0 -> Unit
            1, 2 -> {
                val reason = if (minor >= 8) runCatching { input.readLengthPrefixedText() }.getOrDefault("Authentication failed") else "Authentication failed"
                throw SecurityException(reason)
            }
            else -> throw SecurityException("Unknown RFB security result: $status")
        }
    }

    private fun readServerPixelFormat(input: DataInputStream) {
        input.readU8() // bits per pixel
        input.readU8() // depth
        input.readU8() // endian
        input.readU8() // true color
        input.readU16(); input.readU16(); input.readU16()
        input.readU8(); input.readU8(); input.readU8()
        input.skipBytes(3)
    }

    private fun sendSetPixelFormat(output: DataOutputStream, format: RfbPixelFormat) {
        output.writeByte(0)
        output.write(byteArrayOf(0, 0, 0))
        output.writeByte(format.bitsPerPixel)
        output.writeByte(format.depth)
        output.writeByte(if (format.bigEndian) 1 else 0)
        output.writeByte(if (format.trueColor) 1 else 0)
        output.writeU16(format.redMax)
        output.writeU16(format.greenMax)
        output.writeU16(format.blueMax)
        output.writeByte(format.redShift)
        output.writeByte(format.greenShift)
        output.writeByte(format.blueShift)
        output.write(byteArrayOf(0, 0, 0))
        output.flush()
    }

    private fun sendSetEncodings(output: DataOutputStream, preferRaw: Boolean) {
        val encodings = if (preferRaw) {
            intArrayOf(ENCODING_RAW, ENCODING_COPY_RECT, ENCODING_HEXTILE, ENCODING_DESKTOP_SIZE, ENCODING_LAST_RECT)
        } else {
            intArrayOf(ENCODING_HEXTILE, ENCODING_COPY_RECT, ENCODING_RAW, ENCODING_DESKTOP_SIZE, ENCODING_LAST_RECT)
        }
        output.writeByte(2)
        output.writeByte(0)
        output.writeU16(encodings.size)
        encodings.forEach(output::writeInt)
        output.flush()
    }

    private fun requestFramebufferUpdate(output: DataOutputStream, incremental: Boolean) {
        output.writeByte(3)
        output.writeByte(if (incremental) 1 else 0)
        output.writeU16(0)
        output.writeU16(0)
        output.writeU16(framebufferWidth)
        output.writeU16(framebufferHeight)
        output.flush()
    }

    private suspend fun readServerLoop() {
        val input = input ?: return
        val output = output ?: return
        try {
            while (kotlin.coroutines.coroutineContext.isActive) {
                when (input.readU8()) {
                    0 -> readFramebufferUpdate(input, output)
                    1 -> skipColorMap(input)
                    2 -> Unit // bell
                    3 -> readServerClipboard(input)
                    else -> throw IllegalStateException("Unsupported RFB server message")
                }
            }
        } catch (e: EOFException) {
            _state.value = VncSessionState.Failed("VNC connection closed by server", retryable = true)
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            _state.value = VncSessionState.Failed(sanitizeError(t), retryable = t !is SecurityException)
        } finally {
            runCatching { socket?.close() }
        }
    }

    private suspend fun readFramebufferUpdate(input: DataInputStream, output: DataOutputStream) {
        input.readU8() // padding
        val rectangleCount = input.readU16()
        var dirtyLeft = framebufferWidth
        var dirtyTop = framebufferHeight
        var dirtyRight = 0
        var dirtyBottom = 0

        for (index in 0 until rectangleCount) {
            val x = input.readU16()
            val y = input.readU16()
            val width = input.readU16()
            val height = input.readU16()
            when (val encoding = input.readS32()) {
                ENCODING_RAW -> {
                    readRawRectangle(input, x, y, width, height)
                    dirtyLeft = minOf(dirtyLeft, x)
                    dirtyTop = minOf(dirtyTop, y)
                    dirtyRight = maxOf(dirtyRight, x + width)
                    dirtyBottom = maxOf(dirtyBottom, y + height)
                }
                ENCODING_HEXTILE -> {
                    hextileDecoder.decodeRectangle(
                        input = input,
                        framebuffer = framebuffer,
                        framebufferWidth = framebufferWidth,
                        framebufferHeight = framebufferHeight,
                        x = x,
                        y = y,
                        width = width,
                        height = height,
                    )
                    dirtyLeft = minOf(dirtyLeft, x)
                    dirtyTop = minOf(dirtyTop, y)
                    dirtyRight = maxOf(dirtyRight, x + width)
                    dirtyBottom = maxOf(dirtyBottom, y + height)
                }
                ENCODING_COPY_RECT -> {
                    val srcX = input.readU16()
                    val srcY = input.readU16()
                    copyRectangle(srcX, srcY, x, y, width, height)
                    dirtyLeft = minOf(dirtyLeft, x)
                    dirtyTop = minOf(dirtyTop, y)
                    dirtyRight = maxOf(dirtyRight, x + width)
                    dirtyBottom = maxOf(dirtyBottom, y + height)
                }
                ENCODING_DESKTOP_SIZE -> {
                    setFramebufferSize(width, height)
                    dirtyLeft = 0; dirtyTop = 0; dirtyRight = width; dirtyBottom = height
                    _state.value = VncSessionState.Connected(width, height, currentDesktopName())
                }
                ENCODING_LAST_RECT -> break
                else -> throw IllegalStateException("Unsupported RFB encoding: $encoding")
            }
        }

        val changed = dirtyRight > dirtyLeft && dirtyBottom > dirtyTop
        if (changed) {
            changedFramesInWindow += 1
            _frames.emit(
                VncFrame(
                    width = framebufferWidth,
                    height = framebufferHeight,
                    argb = framebuffer.copyOf(),
                    dirtyLeft = dirtyLeft,
                    dirtyTop = dirtyTop,
                    dirtyRight = dirtyRight,
                    dirtyBottom = dirtyBottom,
                ),
            )
        }
        updatePerformanceWindow()
        synchronized(writerLock) {
            val qualityChanged = applyPendingQuality(output)
            requestFramebufferUpdate(output, incremental = !qualityChanged)
        }
    }


    private fun updatePerformanceWindow(nowNanos: Long = System.nanoTime()) {
        val elapsedNanos = nowNanos - fpsWindowStartedNanos
        if (elapsedNanos < FPS_WINDOW_NANOS) return
        val fps = ((changedFramesInWindow * 1_000_000_000L) / elapsedNanos.coerceAtLeast(1L)).toInt()
        if (selectedQuality == VncQualityMode.AUTO) {
            requestedEffectiveQuality = adaptiveQuality.observeWindow(fps, changedFramesInWindow)
        }
        _performanceStats.value = VncPerformanceStats(
            fps = fps,
            activeQuality = appliedQuality,
        )
        fpsWindowStartedNanos = nowNanos
        changedFramesInWindow = 0
    }

    private fun resetPerformanceWindow() {
        fpsWindowStartedNanos = System.nanoTime()
        changedFramesInWindow = 0
        _performanceStats.value = VncPerformanceStats(
            fps = 0,
            activeQuality = appliedQuality,
        )
    }

    private fun applyPendingQuality(output: DataOutputStream): Boolean {
        val desired = requestedEffectiveQuality
        if (desired == appliedQuality) return false
        applyProfileLocally(desired)
        sendSetPixelFormat(output, pixelFormat)
        sendSetEncodings(output, desired.profileFor().preferRaw)
        _performanceStats.value = _performanceStats.value.copy(activeQuality = appliedQuality)
        return true
    }

    private fun applyProfileLocally(mode: VncQualityMode) {
        val effective = if (mode == VncQualityMode.AUTO) VncQualityMode.BALANCED else mode
        val profile = effective.profileFor()
        pixelFormat = profile.pixelFormat
        hextileDecoder = HextileDecoder(pixelFormat)
        appliedQuality = effective
        _performanceStats.value = _performanceStats.value.copy(activeQuality = effective)
    }

    private fun readRawRectangle(input: DataInputStream, x: Int, y: Int, width: Int, height: Int) {
        require(x >= 0 && y >= 0 && x + width <= framebufferWidth && y + height <= framebufferHeight) {
            "RFB rectangle is outside framebuffer"
        }
        val rowBytes = ByteArray(width * pixelFormat.bytesPerPixel)
        repeat(height) { row ->
            input.readFully(rowBytes)
            val destinationRow = y + row
            repeat(width) { column ->
                framebuffer[destinationRow * framebufferWidth + x + column] =
                    pixelFormat.decodePixel(rowBytes, column * pixelFormat.bytesPerPixel)
            }
        }
    }

    private fun copyRectangle(srcX: Int, srcY: Int, dstX: Int, dstY: Int, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        require(srcX + width <= framebufferWidth && srcY + height <= framebufferHeight)
        require(dstX + width <= framebufferWidth && dstY + height <= framebufferHeight)
        val temp = IntArray(width * height)
        repeat(height) { row ->
            framebuffer.copyInto(temp, row * width, (srcY + row) * framebufferWidth + srcX, (srcY + row) * framebufferWidth + srcX + width)
        }
        repeat(height) { row ->
            temp.copyInto(framebuffer, (dstY + row) * framebufferWidth + dstX, row * width, row * width + width)
        }
    }

    private fun skipColorMap(input: DataInputStream) {
        input.readU8()
        input.readU16()
        val count = input.readU16()
        val bytesToSkip = count * 6
        val skipped = input.skipBytes(bytesToSkip)
        if (skipped != bytesToSkip) throw EOFException("Incomplete RFB color map")
    }

    private fun readServerClipboard(input: DataInputStream) {
        input.skipBytes(3)
        val length = input.readU32()
        require(length <= MAX_CLIPBOARD_BYTES) { "Remote clipboard is too large" }
        val bytes = ByteArray(length.toInt())
        input.readFully(bytes)
        _remoteClipboard.tryEmit(bytes.toString(StandardCharsets.ISO_8859_1))
    }

    private fun sendPointer(out: DataOutputStream, x: Int, y: Int, mask: Int) {
        out.writeByte(5)
        out.writeByte(mask and 0xff)
        out.writeU16(x.coerceIn(0, (framebufferWidth - 1).coerceAtLeast(0)))
        out.writeU16(y.coerceIn(0, (framebufferHeight - 1).coerceAtLeast(0)))
        out.flush()
    }

    private fun sendScroll(out: DataOutputStream, event: VncInputEvent.Scroll) {
        fun click(mask: Int) {
            sendPointer(out, event.x, event.y, mask)
            sendPointer(out, event.x, event.y, 0)
        }
        repeat(kotlin.math.abs(event.deltaY)) { click(if (event.deltaY < 0) TrackpadGestureInterpreter.WHEEL_UP else TrackpadGestureInterpreter.WHEEL_DOWN) }
        repeat(kotlin.math.abs(event.deltaX)) { click(if (event.deltaX < 0) TrackpadGestureInterpreter.WHEEL_LEFT else TrackpadGestureInterpreter.WHEEL_RIGHT) }
    }

    private fun setFramebufferSize(width: Int, height: Int) {
        require(width in 1..16384 && height in 1..16384) { "Invalid framebuffer size: ${width}x${height}" }
        framebufferWidth = width
        framebufferHeight = height
        framebuffer = IntArray(width * height) { 0xff000000.toInt() }
    }

    private fun currentDesktopName(): String = (state.value as? VncSessionState.Connected)?.desktopName.orEmpty()

    private fun sanitizeError(t: Throwable): String = when (t) {
        is SecurityException -> t.message ?: "VNC authentication failed"
        else -> t.message?.take(240) ?: t::class.simpleName ?: "VNC error"
    }

    private data class ProtocolVersion(val major: Int, val minor: Int) {
        val banner: String get() = "RFB %03d.%03d\n".format(major, minor)
    }

    companion object {
        private const val SECURITY_NONE = 1
        private const val SECURITY_VNC = 2
        private const val ENCODING_RAW = 0
        private const val ENCODING_COPY_RECT = 1
        private const val ENCODING_HEXTILE = 5
        private const val ENCODING_DESKTOP_SIZE = -223
        private const val ENCODING_LAST_RECT = -224
        private const val MAX_CLIPBOARD_BYTES = 1024L * 1024L
        private const val FPS_WINDOW_NANOS = 1_000_000_000L
    }
}
