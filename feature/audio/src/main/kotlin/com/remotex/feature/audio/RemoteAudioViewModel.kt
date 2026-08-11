package com.remotex.feature.audio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.remotex.feature.ssh.domain.SshConnectionSpec
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class RemoteAudioViewModel(
    private val engine: RemoteAudioEngine,
) : ViewModel() {
    val state = engine.state
    private var streamJob: Job? = null

    fun start(spec: SshConnectionSpec) {
        if (state.value is RemoteAudioState.Connecting || state.value is RemoteAudioState.Playing) return
        streamJob?.cancel()
        streamJob = viewModelScope.launch { engine.start(spec) }
    }

    fun stop() {
        val running = streamJob
        streamJob = null
        viewModelScope.launch {
            engine.stop()
            running?.cancel()
        }
    }

    override fun onCleared() {
        streamJob?.cancel()
        viewModelScope.launch { engine.stop() }
        super.onCleared()
    }
}
