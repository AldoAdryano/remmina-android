package com.remotex.feature.audio

import com.remotex.feature.ssh.domain.SshConnectionSpec
import kotlinx.coroutines.flow.StateFlow

sealed interface RemoteAudioState {
    data object Idle : RemoteAudioState
    data object Connecting : RemoteAudioState
    data class Playing(val delayMs: Int) : RemoteAudioState
    data class Failed(val reason: String) : RemoteAudioState
}

interface RemoteAudioEngine {
    val state: StateFlow<RemoteAudioState>
    suspend fun start(spec: SshConnectionSpec, initialDelayMs: Int)
    suspend fun stop()
}

internal interface PcmPlayer {
    suspend fun start(initialDelayMs: Int)
    suspend fun write(bytes: ByteArray)
    suspend fun stop()
}
