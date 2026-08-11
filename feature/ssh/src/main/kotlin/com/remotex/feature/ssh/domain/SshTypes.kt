package com.remotex.feature.ssh.domain

import com.remotex.feature.ssh.knownhosts.KnownHostRecord
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

sealed interface SshAuth {
    data class Password(val password: CharArray) : SshAuth
    data class PrivateKey(
        val keyBytes: ByteArray,
        val passphrase: CharArray? = null,
    ) : SshAuth
}

data class SshConnectionSpec(
    val host: String,
    val port: Int = 22,
    val username: String,
    val auth: SshAuth,
)

sealed interface SshSessionState {
    data object Idle : SshSessionState
    data object Connecting : SshSessionState
    data object VerifyingHost : SshSessionState
    data class HostKeyRequired(
        val candidate: KnownHostRecord,
        val previous: KnownHostRecord? = null,
    ) : SshSessionState
    data object Authenticating : SshSessionState
    data object Connected : SshSessionState
    data class Failed(val reason: String, val retryable: Boolean = true) : SshSessionState
    data object Closed : SshSessionState
}

interface ExecChannel {
    val stdout: Flow<ByteArray>
    val stderr: Flow<ByteArray>
    suspend fun close()
}

interface ShellChannel {
    val stdout: Flow<ByteArray>
    val stderr: Flow<ByteArray>
    suspend fun write(bytes: ByteArray)
    suspend fun resize(columns: Int, rows: Int)
    suspend fun close()
}

data class SftpTransportEntry(
    val path: String,
    val name: String,
    val directory: Boolean,
    val size: Long,
    val modifiedAtEpochMillis: Long?,
)

interface SftpTransport {
    suspend fun list(path: String): List<SftpTransportEntry>
    suspend fun exists(path: String): Boolean
    suspend fun mkdir(path: String)
    suspend fun rename(from: String, to: String)
    suspend fun removeFile(path: String)
    suspend fun removeDirectory(path: String)
    suspend fun openRead(path: String): InputStream
    suspend fun openWrite(path: String, truncate: Boolean = true): OutputStream
    suspend fun serverSideCopy(from: String, to: String): Boolean = false
    suspend fun close()
}

interface SshSessionHandle {
    suspend fun openExec(command: String): ExecChannel
    suspend fun openShell(term: String = "xterm-256color", columns: Int = 80, rows: Int = 24): ShellChannel
    suspend fun openSftpTransport(): SftpTransport
    suspend fun close()
}

interface SshEngine {
    val state: StateFlow<SshSessionState>
    suspend fun connect(spec: SshConnectionSpec): SshSessionHandle?
    suspend fun trustHost(record: KnownHostRecord)
    suspend fun disconnect()
}
