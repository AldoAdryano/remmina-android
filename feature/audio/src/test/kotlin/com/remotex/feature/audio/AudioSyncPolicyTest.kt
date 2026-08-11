package com.remotex.feature.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioSyncPolicyTest {
    @Test
    fun delayTracksLatestVideoCadence() {
        assertEquals(180, AudioSyncPolicy.delayMsForFps(0))
        assertEquals(240, AudioSyncPolicy.delayMsForFps(8))
        assertEquals(180, AudioSyncPolicy.delayMsForFps(16))
        assertEquals(120, AudioSyncPolicy.delayMsForFps(30))
    }

    @Test
    fun pcmBytesForDelayIsFrameAligned() {
        assertEquals(34_560, AudioSyncPolicy.pcmBytesForDelay(180, rateHz = 48_000, channels = 2, bytesPerSample = 2))
        assertEquals(23_040, AudioSyncPolicy.pcmBytesForDelay(120, rateHz = 48_000, channels = 2, bytesPerSample = 2))
    }
}
