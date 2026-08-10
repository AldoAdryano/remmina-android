package com.remotex.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.remotex.feature.sftp.presentation.SftpScreen
import com.remotex.feature.sftp.presentation.SftpViewModel
import com.remotex.feature.ssh.domain.SshAuth
import com.remotex.feature.ssh.domain.SshConnectionSpec
import com.remotex.feature.ssh.presentation.SshTerminalScreen
import com.remotex.feature.ssh.presentation.SshViewModel
import com.remotex.feature.vnc.engine.RfbVncEngine
import com.remotex.feature.vnc.presentation.VncScreen
import com.remotex.feature.vnc.presentation.VncViewModel

private enum class QuickProtocol { VNC, SSH, SFTP }

@Composable
fun QuickConnectScreen(container: AppContainer, onBack: () -> Unit) {
    var protocol by remember { mutableStateOf(QuickProtocol.VNC) }
    var host by remember { mutableStateOf("") }
    var port by remember(protocol) { mutableStateOf(if (protocol == QuickProtocol.VNC) "5900" else "22") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var started by remember { mutableStateOf(false) }

    if (!started) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Koneksi cepat")
            Row {
                QuickProtocol.entries.forEach { item ->
                    RadioButton(selected = protocol == item, onClick = {
                        protocol = item
                        port = if (item == QuickProtocol.VNC) "5900" else "22"
                    })
                    Text(item.name, modifier = Modifier.padding(top = 12.dp, end = 8.dp))
                }
            }
            OutlinedTextField(host, { host = it }, label = { Text("Host / IP") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                port, { port = it.filter(Char::isDigit) }, label = { Text("Port") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
            )
            if (protocol != QuickProtocol.VNC) {
                OutlinedTextField(username, { username = it }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth())
            }
            OutlinedTextField(
                password, { password = it }, label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onBack) { Text("Batal") }
                Button(
                    onClick = { started = true },
                    enabled = host.isNotBlank() && (port.toIntOrNull() ?: 0) in 1..65535 && (protocol == QuickProtocol.VNC || username.isNotBlank()),
                ) { Text("Sambungkan") }
            }
            Text("Koneksi cepat tidak disimpan ke daftar profil.")
        }
        return
    }

    when (protocol) {
        QuickProtocol.VNC -> {
            val vm = remember { VncViewModel(RfbVncEngine()) }
            DisposableEffect(vm) {
                val secret = password.toCharArray()
                password = ""
                vm.connect(host, port.toInt(), secret)
                onDispose { vm.disconnect() }
            }
            VncScreen(vm, host, onBack)
        }
        QuickProtocol.SSH -> {
            val vm = remember { SshViewModel(container.newSshEngine()) }
            DisposableEffect(vm) {
                val secret = password.toCharArray()
                password = ""
                vm.connect(SshConnectionSpec(host, port.toInt(), username, SshAuth.Password(secret)))
                onDispose { vm.disconnect() }
            }
            SshTerminalScreen(host, vm, onReconnect = { started = false }, onBack = onBack)
        }
        QuickProtocol.SFTP -> {
            val context = androidx.compose.ui.platform.LocalContext.current
            val vm = remember { SftpViewModel(context.applicationContext, container.newSshEngine()) }
            DisposableEffect(vm) {
                val secret = password.toCharArray()
                password = ""
                vm.connect(SshConnectionSpec(host, port.toInt(), username, SshAuth.Password(secret)))
                onDispose { vm.disconnect() }
            }
            SftpScreen(host, vm, onReconnect = { started = false }, onBack = onBack)
        }
    }
}
