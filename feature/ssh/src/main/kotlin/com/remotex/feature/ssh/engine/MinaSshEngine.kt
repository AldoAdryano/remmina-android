package com.remotex.feature.ssh.engine

import com.remotex.feature.ssh.domain.SftpTransport
import com.remotex.feature.ssh.domain.ShellChannel
import com.remotex.feature.ssh.domain.SshAuth
import com.remotex.feature.ssh.domain.SshConnectionSpec
import com.remotex.feature.ssh.domain.SshEngine
import com.remotex.feature.ssh.domain.SshSessionHandle
import com.remotex.feature.ssh.domain.SshSessionState
import com.remotex.feature.ssh.knownhosts.HostKeyDecision
import com.remotex.feature.ssh.knownhosts.KnownHostPolicy
import com.remotex.feature.ssh.knownhosts.KnownHostRecord
import com.remotex.feature.ssh.knownhosts.KnownHostRepository
import java.net.SocketAddress
import java.security.PublicKey
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.apache.sshd.client.SshClient
import org.apache.sshd.client.channel.ChannelShell
import org.apache.sshd.client.keyverifier.ServerKeyVerifier
import org.apache.sshd.client.session.ClientSession
import org.apache.sshd.common.config.keys.KeyUtils
import org.apache.sshd.core.CoreModuleProperties
import org.apache.sshd.sftp.client.SftpClientFactory

class MinaSshEngine(
    private val knownHosts: KnownHostRepository,
    private val timeout: Duration = Duration.ofSeconds(15),
) : SshEngine {
    private val _state = MutableStateFlow<SshSessionState>(SshSessionState.Idle)
    override val state: StateFlow<SshSessionState> = _state.asStateFlow()

    private var client: SshClient? = null
    private var handle: MinaSessionHandle? = null

    override suspend fun connect(spec: SshConnectionSpec): SshSessionHandle? = withContext(Dispatchers.IO) {
        disconnectInternal()
        require(spec.host.isNotBlank()) { "Host SSH wajib diisi" }
        require(spec.username.isNotBlank()) { "Username SSH wajib diisi" }
        require(spec.port in 1..65535) { "Port SSH tidak valid" }

        _state.value = SshSessionState.Connecting
        val trusted = knownHosts.find(spec.host, spec.port)
        val rejectedCandidate = AtomicReference<KnownHostRecord?>(null)
        val changedPrevious = AtomicReference<KnownHostRecord?>(null)
        val sshClient = SshClient.setUpDefaultClient()
        CoreModuleProperties.HEARTBEAT_INTERVAL.set(sshClient, Duration.ofSeconds(30))
        CoreModuleProperties.HEARTBEAT_NO_REPLY_MAX.set(sshClient, 3)
        sshClient.serverKeyVerifier = object : ServerKeyVerifier {
            override fun verifyServerKey(
                clientSession: ClientSession,
                remoteAddress: SocketAddress,
                serverKey: PublicKey,
            ): Boolean {
                _state.value = SshSessionState.VerifyingHost
                val candidate = KnownHostRecord(
                    host = spec.host,
                    port = spec.port,
                    algorithm = KeyUtils.getKeyType(serverKey),
                    sha256Fingerprint = KeyUtils.getFingerPrint(serverKey),
                    keyBlob = serverKey.encoded.copyOf(),
                    trustedAt = System.currentTimeMillis(),
                )
                return when (val decision = KnownHostPolicy.decide(trusted, candidate)) {
                    HostKeyDecision.Trusted -> true
                    is HostKeyDecision.Unknown -> {
                        rejectedCandidate.set(decision.candidate)
                        false
                    }
                    is HostKeyDecision.Changed -> {
                        changedPrevious.set(decision.old)
                        rejectedCandidate.set(decision.candidate)
                        false
                    }
                }
            }
        }
        client = sshClient
        sshClient.start()

        try {
            val session = sshClient.connect(spec.username, spec.host, spec.port).verify(timeout).session
            _state.value = SshSessionState.Authenticating
            when (val auth = spec.auth) {
                is SshAuth.Password -> {
                    val password = auth.password.concatToString()
                    session.addPasswordIdentity(password)
                }
                is SshAuth.PrivateKey -> {
                    val pairs = PrivateKeyLoader.load(auth.keyBytes, auth.passphrase)
                    require(pairs.isNotEmpty()) { "Private key tidak dapat dibaca" }
                    pairs.forEach(session::addPublicKeyIdentity)
                }
            }
            session.auth().verify(timeout)
            val sessionHandle = MinaSessionHandle(session)
            handle = sessionHandle
            _state.value = SshSessionState.Connected
            sessionHandle
        } catch (t: Throwable) {
            val candidate = rejectedCandidate.get()
            if (candidate != null) {
                _state.value = SshSessionState.HostKeyRequired(candidate, changedPrevious.get())
            } else {
                _state.value = SshSessionState.Failed(safeMessage(t), retryable = true)
            }
            runCatching { sshClient.stop() }
            client = null
            null
        }
    }

    override suspend fun trustHost(record: KnownHostRecord) {
        knownHosts.save(record.copy(trustedAt = System.currentTimeMillis()))
        _state.value = SshSessionState.Idle
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        disconnectInternal()
        _state.value = SshSessionState.Closed
    }

    private suspend fun disconnectInternal() {
        runCatching { handle?.close() }
        handle = null
        runCatching { client?.stop() }
        client = null
    }

    private fun safeMessage(t: Throwable): String {
        val root = generateSequence(t) { it.cause }.last()
        return when {
            root.message.isNullOrBlank() -> root::class.java.simpleName
            else -> root.message!!.take(240)
        }
    }

    private inner class MinaSessionHandle(
        private val session: ClientSession,
    ) : SshSessionHandle {
        override suspend fun openShell(term: String, columns: Int, rows: Int): ShellChannel = withContext(Dispatchers.IO) {
            val channel: ChannelShell = session.createShellChannel()
            channel.setUsePty(true)
            channel.setPtyType(term)
            channel.setPtyColumns(columns.coerceAtLeast(1))
            channel.setPtyLines(rows.coerceAtLeast(1))
            channel.open().verify(timeout)
            MinaShellChannel(
                channel = channel,
                input = channel.invertedIn,
                stdoutStream = channel.invertedOut,
                stderrStream = channel.invertedErr,
            )
        }

        override suspend fun openSftpTransport(): SftpTransport = withContext(Dispatchers.IO) {
            MinaSftpTransport(SftpClientFactory.instance().createSftpClient(session))
        }

        override suspend fun close() = withContext(Dispatchers.IO) {
            runCatching { session.close(false) }
        }
    }
}
