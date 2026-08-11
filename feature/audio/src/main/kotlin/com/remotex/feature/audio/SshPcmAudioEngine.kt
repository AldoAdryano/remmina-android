package com.remotex.feature.audio

import android.content.Context
import com.remotex.feature.ssh.domain.ExecChannel
import com.remotex.feature.ssh.domain.SshAuth
import com.remotex.feature.ssh.domain.SshConnectionSpec
import com.remotex.feature.ssh.domain.SshEngine
import com.remotex.feature.ssh.domain.SshSessionHandle
import com.remotex.feature.ssh.domain.SshSessionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SshPcmAudioEngine private constructor(
    private val sshEngine: SshEngine,
    private val player: PcmPlayer,
) : RemoteAudioEngine {
    constructor(context: Context, sshEngine: SshEngine) : this(
        sshEngine = sshEngine,
        player = AndroidPcmPlayer(context.applicationContext),
    )
    private val _state = MutableStateFlow<RemoteAudioState>(RemoteAudioState.Idle)
    override val state: StateFlow<RemoteAudioState> = _state.asStateFlow()

    private var session: SshSessionHandle? = null
    private var exec: ExecChannel? = null
    @Volatile private var explicitStop = false

    override suspend fun start(spec: SshConnectionSpec) {
        stopInternal(setIdle = false)
        explicitStop = false
        _state.value = RemoteAudioState.Connecting
        var stderrText = ""
        try {
            val openedSession = sshEngine.connect(spec)
            wipeAuth(spec.auth)
            if (openedSession == null) {
                _state.value = RemoteAudioState.Failed(connectionFailure())
                return
            }
            session = openedSession
            val openedExec = openedSession.openExec(RemoteAudioCommand.command)
            exec = openedExec
            player.start()
            _state.value = RemoteAudioState.Playing

            coroutineScope {
                val stderrJob = launch {
                    val builder = StringBuilder()
                    openedExec.stderr.collect { bytes ->
                        if (builder.length < MAX_STDERR_CHARS) {
                            builder.append(bytes.toString(Charsets.UTF_8).take(MAX_STDERR_CHARS - builder.length))
                        }
                        stderrText = builder.toString()
                    }
                }
                try {
                    openedExec.stdout.collect { bytes -> player.write(bytes) }
                } finally {
                    stderrJob.cancelAndJoin()
                }
            }

            if (!explicitStop) {
                _state.value = RemoteAudioState.Failed(RemoteAudioCommand.classifyFailure(stderrText))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            wipeAuth(spec.auth)
            if (!explicitStop) {
                val detail = t.message?.takeIf { it.isNotBlank() }
                _state.value = RemoteAudioState.Failed(
                    detail?.let { RemoteAudioCommand.classifyFailure(it) } ?: "Audio remote gagal",
                )
            }
        } finally {
            stopInternal(setIdle = explicitStop)
        }
    }

    override suspend fun stop() {
        explicitStop = true
        stopInternal(setIdle = true)
    }

    private suspend fun stopInternal(setIdle: Boolean) {
        try { exec?.close() } catch (_: Throwable) { }
        exec = null
        try { player.stop() } catch (_: Throwable) { }
        try { session?.close() } catch (_: Throwable) { }
        session = null
        try { sshEngine.disconnect() } catch (_: Throwable) { }
        if (setIdle) _state.value = RemoteAudioState.Idle
    }

    private fun connectionFailure(): String = when (val sshState = sshEngine.state.value) {
        is SshSessionState.HostKeyRequired ->
            "Kunci host SSH belum dipercaya. Buka Terminal profil ini sekali lalu aktifkan Suara lagi"
        is SshSessionState.Failed -> "Audio SSH: ${sshState.reason}"
        else -> "Koneksi SSH untuk audio gagal"
    }

    private fun wipeAuth(auth: SshAuth) {
        when (auth) {
            is SshAuth.Password -> auth.password.fill('\u0000')
            is SshAuth.PrivateKey -> {
                auth.keyBytes.fill(0)
                auth.passphrase?.fill('\u0000')
            }
        }
    }

    private companion object {
        const val MAX_STDERR_CHARS = 2_048
    }
}
