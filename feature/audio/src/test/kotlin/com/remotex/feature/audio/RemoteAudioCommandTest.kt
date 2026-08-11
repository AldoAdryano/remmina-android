package com.remotex.feature.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteAudioCommandTest {
    @Test
    fun captureCommandUsesFixedPcmFormat() {
        assertEquals(48_000, RemoteAudioCommand.RATE_HZ)
        assertEquals(2, RemoteAudioCommand.CHANNELS)
        assertTrue(RemoteAudioCommand.command.contains("--format=s16le"))
        assertTrue(RemoteAudioCommand.command.contains("--rate=48000"))
        assertTrue(RemoteAudioCommand.command.contains("--channels=2"))
    }

    @Test
    fun missingLinuxAudioToolsHasActionableMessage() {
        assertEquals(
            "Utilitas audio Linux belum tersedia. Jalankan: sudo apt install pulseaudio-utils",
            RemoteAudioCommand.classifyFailure("REMOTEX_AUDIO_MISSING"),
        )
    }

    @Test
    fun missingDefaultSinkHasReadableMessage() {
        assertEquals(
            "Output audio Linux tidak ditemukan",
            RemoteAudioCommand.classifyFailure("REMOTEX_AUDIO_NO_SINK"),
        )
    }
}
