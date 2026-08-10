package com.remotex.feature.ssh.engine

import com.remotex.feature.ssh.domain.ShellChannel
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.sshd.client.channel.ChannelShell

internal class MinaShellChannel(
    private val channel: ChannelShell,
    private val input: OutputStream,
    stdoutStream: InputStream,
    stderrStream: InputStream,
) : ShellChannel {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _stdout = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    private val _stderr = MutableSharedFlow<ByteArray>(extraBufferCapacity = 32)
    override val stdout: Flow<ByteArray> = _stdout.asSharedFlow()
    override val stderr: Flow<ByteArray> = _stderr.asSharedFlow()

    private val stdoutJob: Job = scope.launch { pump(stdoutStream, _stdout) }
    private val stderrJob: Job = scope.launch { pump(stderrStream, _stderr) }

    override suspend fun write(bytes: ByteArray) = withContext(Dispatchers.IO) {
        input.write(bytes)
        input.flush()
    }

    override suspend fun resize(columns: Int, rows: Int) = withContext(Dispatchers.IO) {
        if (columns > 0 && rows > 0 && channel.isOpen) {
            channel.sendWindowChange(columns, rows)
        }
    }

    override suspend fun close() = withContext(Dispatchers.IO) {
        stdoutJob.cancel()
        stderrJob.cancel()
        runCatching { input.close() }
        runCatching { channel.close(false) }
        scope.cancel()
    }

    private suspend fun pump(source: InputStream, sink: MutableSharedFlow<ByteArray>) {
        source.use { stream ->
            val buffer = ByteArray(8192)
            while (scope.isActive) {
                val read = runCatching { stream.read(buffer) }.getOrElse { -1 }
                if (read <= 0) break
                sink.emit(buffer.copyOf(read))
            }
        }
    }
}
