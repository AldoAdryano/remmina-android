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
    )

    VncQualityMode.PERFORMANCE -> VncQualityProfile(
        mode = VncQualityMode.PERFORMANCE,
        pixelFormat = RfbPixelFormat.remoteXPerformance(),
        preferRaw = false,
    )

    VncQualityMode.HIGH -> VncQualityProfile(
        mode = VncQualityMode.HIGH,
        pixelFormat = RfbPixelFormat.remoteXDefault(),
        preferRaw = true,
    )
}
