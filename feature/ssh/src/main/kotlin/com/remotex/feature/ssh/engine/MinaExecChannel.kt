package com.remotex.feature.ssh.engine

import com.remotex.feature.ssh.domain.ExecChannel
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.apache.sshd.client.channel.ChannelExec

internal class MinaExecChannel(
    private val channel: ChannelExec,
    private val stdoutStream: InputStream,
    private val stderrStream: InputStream,
) : ExecChannel {
    override val stdout: Flow<ByteArray> = stream(stdoutStream)
    override val stderr: Flow<ByteArray> = stream(stderrStream)

    override suspend fun close() = withContext(Dispatchers.IO) {
        runCatching { stdoutStream.close() }
        runCatching { stderrStream.close() }
        runCatching { channel.close(false) }
        Unit
    }

    private fun stream(input: InputStream): Flow<ByteArray> = flow {
        val buffer = ByteArray(BUFFER_SIZE)
        while (true) {
            val count = withContext(Dispatchers.IO) { input.read(buffer) }
            if (count < 0) break
            if (count == 0) continue
            emit(buffer.copyOf(count))
        }
    }

    private companion object {
        const val BUFFER_SIZE = 16 * 1024
    }
}
