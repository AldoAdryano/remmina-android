package com.remotex.feature.watch

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteWatchUnitTest {
    @Test
    fun ffmpegCommandContainsSynchronizedVideoAndAudioPipeline() {
        val command = RemoteWatchCommand.build()
        assertTrue(command.contains("command -v ffmpeg"))
        assertTrue(command.contains("-f x11grab"))
        assertTrue(command.contains("-framerate 30"))
        assertTrue(command.contains("pactl info"))
        assertTrue(command.contains(".monitor"))
        assertTrue(command.contains("libx264"))
        assertTrue(command.contains("-c:a aac"))
        assertTrue(command.contains("setpts=PTS-STARTPTS"))
        assertTrue(command.contains("asetpts=PTS-STARTPTS"))
        assertTrue(command.contains("-f mpegts pipe:1"))
    }

    @Test
    fun watchPipeDeliversQueuedBytesAndPropagatesFailure() {
        val pipe = RemoteWatchPipe(maxChunks = 2)
        pipe.offer(byteArrayOf(1, 2, 3))
        pipe.offer(byteArrayOf(4, 5))

        val out = ByteArray(4)
        assertEquals(3, pipe.read(out, 0, out.size))
        assertEquals(listOf<Byte>(1, 2, 3), out.take(3))
        assertEquals(2, pipe.read(out, 0, out.size))
        assertEquals(listOf<Byte>(4, 5), out.take(2))
        pipe.close()
        assertEquals(-1, pipe.read(out, 0, out.size))

        val failedPipe = RemoteWatchPipe(maxChunks = 2)
        failedPipe.fail(IOException("boom"))
        val failure = runCatching { failedPipe.read(out, 0, out.size) }.exceptionOrNull()
        assertTrue(failure is IOException)
        assertEquals("boom", failure?.message)
    }
}
