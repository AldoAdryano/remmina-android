package com.remotex.android

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.remotex.core.model.AuthenticationMode
import com.remotex.feature.connections.readBounded
import com.remotex.feature.ssh.domain.SshAuth

@Composable
fun PasswordPrompt(
    title: String,
    onSubmit: (CharArray) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title)
        OutlinedTextField(
            password,
            { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = {
            val chars = password.toCharArray()
            password = ""
            onSubmit(chars)
        }, enabled = password.isNotEmpty()) { Text("Sambungkan") }
    }
}

@Composable
fun SshAuthPrompt(
    mode: AuthenticationMode,
    onSubmit: (SshAuth) -> Unit,
) {
    val context = LocalContext.current
    var password by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }
    var keyBytes by remember { mutableStateOf<ByteArray?>(null) }
    var keyName by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBounded(1024 * 1024) } }
                .onSuccess { bytes ->
                    keyBytes?.fill(0)
                    keyBytes = bytes
                    keyName = uri.lastPathSegment?.substringAfterLast('/') ?: "private-key"
                }
                .onFailure { error = "Private key tidak dapat dibaca" }
        }
    }

    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Autentikasi SSH")
        if (mode == AuthenticationMode.PASSWORD) {
            OutlinedTextField(
                password, { password = it }, label = { Text("Password SSH") },
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = {
                val secret = password.toCharArray()
                password = ""
                onSubmit(SshAuth.Password(secret))
            }, enabled = password.isNotEmpty()) { Text("Sambungkan") }
        } else {
            OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }) {
                Text(keyName?.let { "Key: $it" } ?: "Pilih private key")
            }
            if (mode == AuthenticationMode.PRIVATE_KEY_WITH_PASSPHRASE) {
                OutlinedTextField(
                    passphrase, { passphrase = it }, label = { Text("Passphrase") },
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                )
            }
            Button(onClick = {
                val key = keyBytes?.copyOf() ?: return@Button
                val pass = passphrase.takeIf { it.isNotEmpty() }?.toCharArray()
                keyBytes?.fill(0)
                keyBytes = null
                passphrase = ""
                onSubmit(SshAuth.PrivateKey(key, pass))
            }, enabled = keyBytes != null) { Text("Sambungkan") }
        }
        error?.let { Text(it) }
    }
}
