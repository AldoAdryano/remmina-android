package com.remotex.feature.ssh.terminal

import com.remotex.feature.ssh.domain.ShellChannel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.merge

@OptIn(ExperimentalCoroutinesApi::class)
class TerminalSessionController(
    private val shell: ShellChannel,
) {
    val output: Flow<ByteArray> = merge(shell.stdout, shell.stderr)

    suspend fun sendText(text: String) {
        if (text.isEmpty()) return
        shell.write(text.encodeToByteArray())
    }

    suspend fun sendBytes(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        shell.write(bytes)
    }

    suspend fun sendSpecialKey(key: SpecialKey) {
        shell.write(SpecialKeyEncoder.encode(key))
    }

    suspend fun resize(columns: Int, rows: Int) {
        if (columns <= 0 || rows <= 0) return
        shell.resize(columns, rows)
    }

    suspend fun close() {
        shell.close()
    }
}
