package com.remotex.feature.watch

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.remotex.feature.ssh.domain.ExecChannel
import com.remotex.feature.ssh.domain.SshAuth
import com.remotex.feature.ssh.domain.SshConnectionSpec
import com.remotex.feature.ssh.domain.SshEngine
import com.remotex.feature.ssh.domain.SshSessionHandle
import com.remotex.feature.ssh.domain.SshSessionState
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(UnstableApi::class)
class RemoteWatchController(
    context: Context,
    private val sshEngine: SshEngine,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow<RemoteWatchState>(RemoteWatchState.Idle)
    val state: StateFlow<RemoteWatchState> = _state.asStateFlow()

    private val exoPlayer: ExoPlayer = ExoPlayer.Builder(context.applicationContext)
        .setLoadControl(
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    MIN_BUFFER_MS,
                    MAX_BUFFER_MS,
                    BUFFER_FOR_PLAYBACK_MS,
                    BUFFER_AFTER_REBUFFER_MS,
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .build(),
        )
        .build()
    val player: Player get() = exoPlayer

    private var session: SshSessionHandle? = null
    private var exec: ExecChannel? = null
    private var pipe: RemoteWatchPipe? = null
    private var stdoutJob: Job? = null
    private var stderrJob: Job? = null
    private var stderrText = ""
    @Volatile private var explicitStop = false

    init {
        exoPlayer.addListener(
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (explicitStop) return
                    when (playbackState) {
                        Player.STATE_BUFFERING -> if (_state.value !is RemoteWatchState.Connecting) {
                            _state.value = RemoteWatchState.Buffering
                        }
                        Player.STATE_READY -> _state.value = RemoteWatchState.Playing()
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    if (explicitStop) return
                    if (_state.value !is RemoteWatchState.Failed) {
                        _state.value = RemoteWatchState.Failed(
                            "Pemutar Mode Menonton: ${error.errorCodeName}",
                        )
                    }
                    scope.launch { stopTransportOnly() }
                }
            },
        )
    }

    fun startAsync(spec: SshConnectionSpec) {
        scope.launch { start(spec) }
    }

    fun stopAsync() {
        scope.launch { stop() }
    }

    fun releaseAsync() {
        scope.launch { release() }
    }

    suspend fun start(spec: SshConnectionSpec) {
        stopInternal(setIdle = false)
        explicitStop = false
        _state.value = RemoteWatchState.Connecting
        stderrText = ""

        try {
            val openedSession = sshEngine.connect(spec)
            wipeAuth(spec.auth)
            if (openedSession == null) {
                _state.value = RemoteWatchState.Failed(connectionFailure())
                return
            }
            session = openedSession
            val openedExec = openedSession.openExec(RemoteWatchCommand.build())
            exec = openedExec
            val openedPipe = RemoteWatchPipe()
            pipe = openedPipe

            stderrJob = scope.launch(Dispatchers.IO) {
                val builder = StringBuilder()
                openedExec.stderr.collect { bytes ->
                    if (builder.length < MAX_STDERR_CHARS) {
                        builder.append(bytes.toString(Charsets.UTF_8).take(MAX_STDERR_CHARS - builder.length))
                    }
                    stderrText = builder.toString()
                }
            }

            stdoutJob = scope.launch(Dispatchers.IO) {
                try {
                    openedExec.stdout.collect { bytes -> openedPipe.offer(bytes) }
                    openedPipe.close()
                    stderrJob?.join()
                    if (!explicitStop && _state.value !is RemoteWatchState.Failed) {
                        _state.value = RemoteWatchState.Failed(RemoteWatchCommand.classifyFailure(stderrText))
                        scope.launch { stopTransportOnly() }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (t: Throwable) {
                    openedPipe.fail(IOException("SSH media stream gagal", t))
                    if (!explicitStop) {
                        _state.value = RemoteWatchState.Failed(
                            t.message?.takeIf(String::isNotBlank)?.let { "Mode Menonton: ${it.take(180)}" }
                                ?: "Stream Mode Menonton gagal",
                        )
                        scope.launch { stopTransportOnly() }
                    }
                }
            }

            val mediaItem = MediaItem.Builder()
                .setUri(Uri.parse(WATCH_URI))
                .setMimeType(MimeTypes.VIDEO_MP2T)
                .build()
            val mediaSource = ProgressiveMediaSource.Factory(SshPipeDataSource.Factory(openedPipe))
                .createMediaSource(mediaItem)

            withContext(Dispatchers.Main.immediate) {
                exoPlayer.setMediaSource(mediaSource)
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            wipeAuth(spec.auth)
            _state.value = RemoteWatchState.Failed(
                t.message?.takeIf(String::isNotBlank)?.let { RemoteWatchCommand.classifyFailure(it) }
                    ?: "Mode Menonton gagal",
            )
            stopTransportOnly()
        }
    }

    suspend fun stop() {
        explicitStop = true
        stopInternal(setIdle = true)
    }

    suspend fun release() {
        explicitStop = true
        stopInternal(setIdle = true)
        withContext(Dispatchers.Main.immediate) { exoPlayer.release() }
        scope.cancel()
    }

    private suspend fun stopInternal(setIdle: Boolean) {
        withContext(Dispatchers.Main.immediate) {
            exoPlayer.playWhenReady = false
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
        }
        stopTransportOnly()
        if (setIdle) _state.value = RemoteWatchState.Idle
    }

    private suspend fun stopTransportOnly() {
        pipe?.abort()
        stdoutJob?.cancelAndJoin()
        stdoutJob = null
        stderrJob?.cancelAndJoin()
        stderrJob = null
        pipe = null
        try { exec?.close() } catch (_: Throwable) { }
        exec = null
        try { session?.close() } catch (_: Throwable) { }
        session = null
        try { sshEngine.disconnect() } catch (_: Throwable) { }
    }

    private fun connectionFailure(): String = when (val sshState = sshEngine.state.value) {
        is SshSessionState.HostKeyRequired ->
            "Kunci host SSH belum dipercaya. Buka Terminal profil ini sekali lalu coba Mode Menonton lagi"
        is SshSessionState.Failed -> "SSH Mode Menonton: ${sshState.reason}"
        else -> "Koneksi SSH Mode Menonton gagal"
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

    companion object {
        const val MIN_BUFFER_MS = 2_500
        const val MAX_BUFFER_MS = 5_000
        const val BUFFER_FOR_PLAYBACK_MS = 1_800
        const val BUFFER_AFTER_REBUFFER_MS = 2_500
        private const val MAX_STDERR_CHARS = 4_096
        private const val WATCH_URI = "remotex-watch://stream/live.ts"
    }
}
