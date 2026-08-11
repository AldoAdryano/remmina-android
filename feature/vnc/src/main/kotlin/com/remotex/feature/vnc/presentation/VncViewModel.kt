package com.remotex.feature.vnc.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.remotex.feature.vnc.domain.VncConnectionSpec
import com.remotex.feature.vnc.domain.VncFrame
import com.remotex.feature.vnc.domain.VncInputEvent
import com.remotex.feature.vnc.domain.VncInputMode
import com.remotex.feature.vnc.domain.VncScaleMode
import com.remotex.feature.vnc.domain.VncSessionState
import com.remotex.feature.vnc.engine.VncEngine
import com.remotex.feature.vnc.quality.VncQualityMode
import com.remotex.feature.vnc.session.ReconnectPolicy
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class VncViewModel(
    private val engine: VncEngine,
    private val reconnectPolicy: ReconnectPolicy = ReconnectPolicy(),
) : ViewModel() {
    private val _sessionState = MutableStateFlow<VncSessionState>(VncSessionState.Idle)
    val sessionState: StateFlow<VncSessionState> = _sessionState.asStateFlow()
    val remoteClipboard = engine.remoteClipboard
    val performanceStats = engine.performanceStats

    private val _frame = MutableStateFlow<VncFrame?>(null)
    val frame: StateFlow<VncFrame?> = _frame.asStateFlow()

    private val _inputMode = MutableStateFlow(VncInputMode.TRACKPAD)
    val inputMode = _inputMode.asStateFlow()

    private val _scaleMode = MutableStateFlow(VncScaleMode.FIT_SCREEN)
    val scaleMode = _scaleMode.asStateFlow()

    private val _qualityMode = MutableStateFlow(VncQualityMode.BALANCED)
    val qualityMode = _qualityMode.asStateFlow()

    private var currentSpec: VncConnectionSpec? = null
    private var sessionPassword: CharArray? = null
    private var reconnectJob: Job? = null
    private var reconnectAttempt = 0
    private var explicitDisconnect = false

    init {
        viewModelScope.launch { engine.frames.collect { _frame.value = it } }
        viewModelScope.launch { engine.qualityFallbacks.collect { mode -> _qualityMode.value = mode } }
        viewModelScope.launch {
            engine.state.collect { state ->
                _sessionState.value = state
                if (state is VncSessionState.Connected) reconnectAttempt = 0
                if (state is VncSessionState.Failed && state.retryable && !explicitDisconnect) scheduleReconnect()
            }
        }
    }

    fun connect(host: String, port: Int, password: CharArray?, shared: Boolean = true) {
        explicitDisconnect = false
        reconnectAttempt = 0
        sessionPassword?.fill('\u0000')
        sessionPassword = password?.copyOf()
        currentSpec = VncConnectionSpec(host, port, sessionPassword, shared)
        password?.fill('\u0000')
        viewModelScope.launch { engine.connect(requireNotNull(currentSpec)) }
    }

    fun send(event: VncInputEvent) {
        viewModelScope.launch { engine.send(event) }
    }

    fun setInputMode(mode: VncInputMode) { _inputMode.value = mode }
    fun setScaleMode(mode: VncScaleMode) { _scaleMode.value = mode }
    fun setQualityMode(mode: VncQualityMode) {
        _qualityMode.value = mode
        viewModelScope.launch { engine.setQualityMode(mode) }
    }

    fun setFrameUpdatesEnabled(enabled: Boolean) {
        viewModelScope.launch { engine.setFrameUpdatesEnabled(enabled) }
    }

    fun disconnect() {
        explicitDisconnect = true
        reconnectJob?.cancel()
        currentSpec = null
        sessionPassword?.fill('\u0000')
        sessionPassword = null
        viewModelScope.launch { engine.disconnect() }
    }

    fun reconnectNow() {
        reconnectJob?.cancel()
        reconnectAttempt = 0
        currentSpec?.let { spec -> viewModelScope.launch { engine.connect(spec) } }
    }

    private fun scheduleReconnect() {
        if (reconnectJob?.isActive == true) return
        reconnectAttempt += 1
        val delayMs = reconnectPolicy.delayMillis(reconnectAttempt) ?: return
        val spec = currentSpec ?: return
        _sessionState.value = VncSessionState.Reconnecting(reconnectAttempt)
        reconnectJob = viewModelScope.launch {
            delay(delayMs)
            if (!explicitDisconnect) {
                engine.connect(spec)
            }
        }
    }

    override fun onCleared() {
        explicitDisconnect = true
        sessionPassword?.fill('\u0000')
        sessionPassword = null
        super.onCleared()
    }
}
