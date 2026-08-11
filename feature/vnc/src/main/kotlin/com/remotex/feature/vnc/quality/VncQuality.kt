package com.remotex.feature.vnc.quality

import com.remotex.feature.vnc.protocol.RfbPixelFormat

enum class VncQualityMode {
    AUTO,
    PERFORMANCE,
    BALANCED,
    HIGH,
}

data class VncQualityProfile(
    val mode: VncQualityMode,
    val pixelFormat: RfbPixelFormat,
    val preferRaw: Boolean,
    val tightJpegQuality: Int,
    val tightCompressionLevel: Int,
)

data class VncPerformanceStats(
    val fps: Int = 0,
    val activeQuality: VncQualityMode = VncQualityMode.BALANCED,
)

fun VncQualityMode.profileFor(): VncQualityProfile = when (this) {
    VncQualityMode.AUTO,
    VncQualityMode.BALANCED,
    -> VncQualityProfile(
        mode = VncQualityMode.BALANCED,
        pixelFormat = RfbPixelFormat.remoteXDefault(),
        preferRaw = false,
        tightJpegQuality = 7,
        tightCompressionLevel = 2,
    )

    VncQualityMode.PERFORMANCE -> VncQualityProfile(
        mode = VncQualityMode.PERFORMANCE,
        pixelFormat = RfbPixelFormat.remoteXPerformance(),
        preferRaw = false,
        tightJpegQuality = 4,
        tightCompressionLevel = 1,
    )

    VncQualityMode.HIGH -> VncQualityProfile(
        mode = VncQualityMode.HIGH,
        pixelFormat = RfbPixelFormat.remoteXDefault(),
        preferRaw = false,
        tightJpegQuality = 9,
        tightCompressionLevel = 3,
    )
}
