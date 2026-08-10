package com.remotex.feature.ssh.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.remotex.feature.ssh.domain.SshSessionState
import com.remotex.feature.ssh.terminal.SpecialKey
import org.connectbot.terminal.Terminal
import org.connectbot.terminal.TerminalEmulatorFactory

@Composable
fun SshTerminalScreen(
    title: String,
    viewModel: SshViewModel,
    onReconnect: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val ctrlArmed by viewModel.ctrlArmed.collectAsState()
    val altArmed by viewModel.altArmed.collectAsState()
    val commandHistory by viewModel.commandHistory.collectAsState()
    val clipboard = LocalClipboardManager.current
    var fontSize by remember { mutableStateOf(14) }
    var darkTerminal by remember { mutableStateOf(true) }
    var commandDialog by remember { mutableStateOf(false) }
    var commandText by remember { mutableStateOf("") }

    val emulator = remember {
        TerminalEmulatorFactory.create(
            initialRows = 24,
            initialCols = 80,
            onKeyboardInput = { bytes -> viewModel.sendKeyboardInput(bytes) },
            onResize = { dimensions -> viewModel.resize(dimensions.columns, dimensions.rows) },
            onClipboardCopy = { text -> clipboard.setText(AnnotatedString(text)) },
        )
    }

    LaunchedEffect(viewModel, emulator) {
        viewModel.terminalOutput.collect { bytes -> emulator.writeInput(bytes) }
    }

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = {
                viewModel.disconnect()
                onBack()
            }) { Text("Putus") }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            key(fontSize, darkTerminal) {
                Terminal(
                    terminalEmulator = emulator,
                    modifier = Modifier.fillMaxSize(),
                    initialFontSize = fontSize.sp,
                    minFontSize = 8.sp,
                    maxFontSize = 30.sp,
                    backgroundColor = if (darkTerminal) Color.Black else Color.White,
                    foregroundColor = if (darkTerminal) Color(0xFFE8EAED) else Color(0xFF202124),
                    keyboardEnabled = true,
                    showSoftKeyboard = true,
                    onPasteRequest = {
                        clipboard.getText()?.text?.takeIf { it.isNotEmpty() }?.let(viewModel::sendText)
                    },
                )
            }

            when (val current = state) {
                SshSessionState.Idle,
                SshSessionState.Connecting,
                SshSessionState.VerifyingHost,
                SshSessionState.Authenticating -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is SshSessionState.Failed -> FailurePanel(
                    current.reason,
                    onReconnect,
                    Modifier.align(Alignment.Center),
                )
                else -> Unit
            }
        }

        TerminalToolbar(
            ctrlArmed = ctrlArmed,
            altArmed = altArmed,
            onToggleCtrl = viewModel::toggleCtrl,
            onToggleAlt = viewModel::toggleAlt,
            onSpecialKey = viewModel::sendSpecialKey,
            onPaste = { clipboard.getText()?.text?.let(viewModel::sendText) },
            onCommand = { commandDialog = true },
            onFontSmaller = { fontSize = (fontSize - 1).coerceAtLeast(8) },
            onFontLarger = { fontSize = (fontSize + 1).coerceAtMost(30) },
            onToggleColors = { darkTerminal = !darkTerminal },
        )
    }

    if (commandDialog) {
        AlertDialog(
            onDismissRequest = { commandDialog = false },
            title = { Text("Perintah cepat & riwayat") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Hanya perintah yang dikirim dari dialog ini yang disimpan dalam riwayat sesi. Input terminal biasa tidak direkam.")
                    OutlinedTextField(
                        value = commandText,
                        onValueChange = { commandText = it },
                        label = { Text("Perintah") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (commandHistory.isNotEmpty()) {
                        Text("Riwayat sesi", style = MaterialTheme.typography.labelLarge)
                        LazyColumn(modifier = Modifier.fillMaxWidth().height(140.dp)) {
                            items(commandHistory.take(20)) { command ->
                                TextButton(onClick = { commandText = command }) { Text(command) }
                            }
                        }
                        TextButton(onClick = viewModel::clearCommandHistory) { Text("Hapus riwayat") }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val command = commandText.trim()
                    if (command.isNotEmpty()) viewModel.sendCommand(command)
                    commandText = ""
                    commandDialog = false
                }) { Text("Kirim") }
            },
            dismissButton = { TextButton(onClick = { commandDialog = false }) { Text("Batal") } },
        )
    }

    val hostKeyState = state as? SshSessionState.HostKeyRequired
    if (hostKeyState != null) {
        val changed = hostKeyState.previous != null
        AlertDialog(
            onDismissRequest = viewModel::rejectHost,
            title = { Text(if (changed) "Kunci host berubah" else "Host SSH belum dikenal") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (changed) {
                        Text("PERINGATAN: fingerprint server berubah. Pastikan perubahan ini memang disengaja sebelum mempercayainya.")
                        Text("Sebelumnya: ${hostKeyState.previous?.sha256Fingerprint}")
                    }
                    Text("Algoritma: ${hostKeyState.candidate.algorithm}")
                    Text("Fingerprint: ${hostKeyState.candidate.sha256Fingerprint}")
                }
            },
            confirmButton = {
                Button(onClick = viewModel::trustHostAndRetry) {
                    Text(if (changed) "Ganti kunci tepercaya" else "Percayai")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = viewModel::rejectHost) { Text("Tolak") }
            },
        )
    }
}

@Composable
private fun TerminalToolbar(
    ctrlArmed: Boolean,
    altArmed: Boolean,
    onToggleCtrl: () -> Unit,
    onToggleAlt: () -> Unit,
    onSpecialKey: (SpecialKey) -> Unit,
    onPaste: () -> Unit,
    onCommand: () -> Unit,
    onFontSmaller: () -> Unit,
    onFontLarger: () -> Unit,
    onToggleColors: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SmallKey(if (ctrlArmed) "Ctrl ✓" else "Ctrl", onToggleCtrl)
        SmallKey(if (altArmed) "Alt ✓" else "Alt", onToggleAlt)
        SmallKey("Ctrl+C") { onSpecialKey(SpecialKey.CTRL_C) }
        SmallKey("Ctrl+D") { onSpecialKey(SpecialKey.CTRL_D) }
        SmallKey("Esc") { onSpecialKey(SpecialKey.ESC) }
        SmallKey("Tab") { onSpecialKey(SpecialKey.TAB) }
        SmallKey("↑") { onSpecialKey(SpecialKey.ARROW_UP) }
        SmallKey("↓") { onSpecialKey(SpecialKey.ARROW_DOWN) }
        SmallKey("←") { onSpecialKey(SpecialKey.ARROW_LEFT) }
        SmallKey("→") { onSpecialKey(SpecialKey.ARROW_RIGHT) }
        SmallKey("Tempel", onPaste)
        SmallKey("Perintah", onCommand)
        SmallKey("A−", onFontSmaller)
        SmallKey("A+", onFontLarger)
        SmallKey("Warna", onToggleColors)
    }
}

@Composable
private fun SmallKey(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick) { Text(label) }
}

@Composable
private fun FailurePanel(reason: String, onReconnect: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Koneksi SSH gagal", style = MaterialTheme.typography.titleMedium)
        Text(reason, modifier = Modifier.padding(vertical = 8.dp))
        Button(onClick = onReconnect) { Text("Sambungkan ulang") }
    }
}
