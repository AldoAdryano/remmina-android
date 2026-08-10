package com.remotex.feature.ssh.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.remotex.feature.ssh.domain.SshConnectionSpec
import com.remotex.feature.ssh.domain.SshEngine
import com.remotex.feature.ssh.domain.SshSessionHandle
import com.remotex.feature.ssh.domain.SshSessionState
import com.remotex.feature.ssh.terminal.SpecialKey
import com.remotex.feature.ssh.terminal.TerminalCommandHistory
import com.remotex.feature.ssh.terminal.TerminalSessionController
import com.remotex.feature.ssh.terminal.TerminalModifierEncoder
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SshViewModel(
    private val engine: SshEngine,
) : ViewModel() {
    val state: StateFlow<SshSessionState> = engine.state

    private val _terminalOutput = MutableSharedFlow<ByteArray>(extraBufferCapacity = 128)
    val terminalOutput: SharedFlow<ByteArray> = _terminalOutput
    private val _ctrlArmed = MutableStateFlow(false)
    val ctrlArmed: StateFlow<Boolean> = _ctrlArmed
    private val _altArmed = MutableStateFlow(false)
    val altArmed: StateFlow<Boolean> = _altArmed
    private val commandHistoryStore = TerminalCommandHistory(maxEntries = 100)
    private val _commandHistory = MutableStateFlow<List<String>>(emptyList())
    val commandHistory: StateFlow<List<String>> = _commandHistory

    private var pendingSpec: SshConnectionSpec? = null
    private var session: SshSessionHandle? = null
    private var terminal: TerminalSessionController? = null
    private var outputJob: Job? = null

    fun connect(spec: SshConnectionSpec) {
        pendingSpec = spec
        viewModelScope.launch { connectInternal(spec) }
    }

    fun trustHostAndRetry() {
        val hostState = state.value as? SshSessionState.HostKeyRequired ?: return
        val spec = pendingSpec ?: return
        viewModelScope.launch {
            engine.trustHost(hostState.candidate)
            connectInternal(spec)
        }
    }

    fun rejectHost() {
        viewModelScope.launch {
            clearPendingSecret()
            engine.disconnect()
        }
    }

    fun sendBytes(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        viewModelScope.launch { terminal?.sendBytes(bytes) }
    }

    fun sendKeyboardInput(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        val encoded = TerminalModifierEncoder.apply(bytes, _ctrlArmed.value, _altArmed.value)
        _ctrlArmed.value = false
        _altArmed.value = false
        viewModelScope.launch { terminal?.sendBytes(encoded) }
    }

    fun toggleCtrl() { _ctrlArmed.value = !_ctrlArmed.value }
    fun toggleAlt() { _altArmed.value = !_altArmed.value }

    fun sendText(text: String) {
        if (text.isEmpty()) return
        viewModelScope.launch { terminal?.sendText(text) }
    }

    fun sendCommand(command: String) {
        val normalized = command.trim()
        if (normalized.isEmpty()) return
        commandHistoryStore.record(normalized)
        _commandHistory.value = commandHistoryStore.entries
        viewModelScope.launch {
            terminal?.sendText(normalized)
            terminal?.sendSpecialKey(SpecialKey.ENTER)
        }
    }

    fun clearCommandHistory() {
        commandHistoryStore.clear()
        _commandHistory.value = emptyList()
    }

    fun sendSpecialKey(key: SpecialKey) {
        viewModelScope.launch { terminal?.sendSpecialKey(key) }
    }

    fun resize(columns: Int, rows: Int) {
        viewModelScope.launch { terminal?.resize(columns, rows) }
    }

    fun disconnect() {
        viewModelScope.launch {
            closeTerminal()
            engine.disconnect()
            _ctrlArmed.value = false
            _altArmed.value = false
            clearPendingSecret()
        }
    }

    private suspend fun connectInternal(spec: SshConnectionSpec) {
        closeTerminal()
        val openedSession = engine.connect(spec) ?: run {
            if (state.value !is SshSessionState.HostKeyRequired) clearPendingSecret()
            return
        }
        session = openedSession
        val shell = openedSession.openShell(term = "xterm-256color", columns = 80, rows = 24)
        val controller = TerminalSessionController(shell)
        terminal = controller
        outputJob = viewModelScope.launch {
            controller.output.collect { _terminalOutput.emit(it) }
        }
        clearPendingSecret()
    }

    private suspend fun closeTerminal() {
        outputJob?.cancel()
        outputJob = null
        terminal?.close()
        terminal = null
        session?.close()
        session = null
    }

    private fun clearPendingSecret() {
        when (val auth = pendingSpec?.auth) {
            is com.remotex.feature.ssh.domain.SshAuth.Password -> auth.password.fill('\u0000')
            is com.remotex.feature.ssh.domain.SshAuth.PrivateKey -> {
                auth.keyBytes.fill(0)
                auth.passphrase?.fill('\u0000')
            }
            null -> Unit
        }
        pendingSpec = null
    }

    override fun onCleared() {
        viewModelScope.launch {
            closeTerminal()
            engine.disconnect()
            _ctrlArmed.value = false
            _altArmed.value = false
            clearPendingSecret()
        }
        super.onCleared()
    }
}
