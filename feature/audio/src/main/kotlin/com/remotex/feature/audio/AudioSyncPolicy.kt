package com.remotex.feature.audio

/** Pragmatic A/V delay for RFB video plus independent SSH PCM audio. */
object AudioSyncPolicy {
    fun delayMsForFps(fps: Int): Int = when {
        fps <= 0 -> 180
        fps < 12 -> 240
        fps < 24 -> 180
        else -> 120
    }

    fun pcmBytesForDelay(delayMs: Int, rateHz: Int, channels: Int, bytesPerSample: Int): Int {
        require(delayMs in 0..1_000)
        require(rateHz > 0 && channels > 0 && bytesPerSample > 0)
        val bytes = rateHz.toLong() * channels * bytesPerSample * delayMs / 1_000L
        require(bytes <= Int.MAX_VALUE)
        val frameBytes = channels * bytesPerSample
        return ((bytes / frameBytes) * frameBytes).toInt()
    }
}
