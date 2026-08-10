package com.remotex.feature.connections

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.remotex.core.model.AuthenticationMode
import com.remotex.core.model.ConnectionProfile
import com.remotex.core.model.CredentialPolicy

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionEditorScreen(
    initial: ConnectionProfile?,
    onSave: (ConnectionProfile, CharArray?, CharArray?, ByteArray?, CharArray?) -> Unit,
    onCancel: () -> Unit,
) {
    var name by rememberSaveable(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var host by rememberSaveable(initial?.id) { mutableStateOf(initial?.host.orEmpty()) }
    var username by rememberSaveable(initial?.id) { mutableStateOf(initial?.username.orEmpty()) }
    var notes by rememberSaveable(initial?.id) { mutableStateOf(initial?.notes.orEmpty()) }
    var favorite by rememberSaveable(initial?.id) { mutableStateOf(initial?.favorite ?: false) }
    var vncEnabled by rememberSaveable(initial?.id) { mutableStateOf(initial?.vncEnabled ?: true) }
    var vncPort by rememberSaveable(initial?.id) { mutableStateOf((initial?.vncPort ?: 5900).toString()) }
    var sshEnabled by rememberSaveable(initial?.id) { mutableStateOf(initial?.sshEnabled ?: true) }
    var sshPort by rememberSaveable(initial?.id) { mutableStateOf((initial?.sshPort ?: 22).toString()) }
    var authMode by rememberSaveable(initial?.id) { mutableStateOf(initial?.authenticationMode ?: AuthenticationMode.PASSWORD) }
    var policy by rememberSaveable(initial?.id) { mutableStateOf(initial?.credentialPolicy ?: CredentialPolicy.ALWAYS_ASK) }
    var sshPassword by remember { mutableStateOf("") }
    var vncPassword by remember { mutableStateOf("") }
    var keyPassphrase by remember { mutableStateOf("") }
    var privateKeyBytes by remember { mutableStateOf<ByteArray?>(null) }
    var privateKeyName by remember { mutableStateOf<String?>(null) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val keyPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBounded(1024 * 1024) }
            }.onSuccess { bytes ->
                privateKeyBytes?.fill(0)
                privateKeyBytes = bytes
                privateKeyName = uri.lastPathSegment?.substringAfterLast('/') ?: "private-key"
            }.onFailure { error = "Private key tidak dapat dibaca." }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(if (initial == null) "Tambah koneksi" else "Edit koneksi") }) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(name, { name = it }, label = { Text("Nama koneksi") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(host, { host = it }, label = { Text("Host / IP") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(username, { username = it }, label = { Text("Username SSH/SFTP") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Aktifkan VNC")
                Switch(vncEnabled, onCheckedChange = { vncEnabled = it })
            }
            if (vncEnabled) {
                OutlinedTextField(
                    vncPort, { vncPort = it.filter(Char::isDigit) }, label = { Text("Port VNC") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true,
                )
                if (policy == CredentialPolicy.SAVE_SECURELY) {
                    OutlinedTextField(
                        vncPassword, { vncPassword = it }, label = { Text("Password VNC (kosong = pertahankan)") },
                        visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true,
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Aktifkan SSH/SFTP")
                Switch(sshEnabled, onCheckedChange = { sshEnabled = it })
            }
            if (sshEnabled) {
                OutlinedTextField(
                    sshPort, { sshPort = it.filter(Char::isDigit) }, label = { Text("Port SSH/SFTP") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true,
                )
                Text("Autentikasi SSH")
                AuthenticationMode.entries.forEach { mode ->
                    Row {
                        RadioButton(selected = authMode == mode, onClick = {
                            authMode = mode
                            sshPassword = ""
                            keyPassphrase = ""
                            if (mode == AuthenticationMode.PASSWORD) {
                                privateKeyBytes?.fill(0)
                                privateKeyBytes = null
                                privateKeyName = null
                            }
                        })
                        Text(
                            when (mode) {
                                AuthenticationMode.PASSWORD -> "Password"
                                AuthenticationMode.PRIVATE_KEY -> "Private key"
                                AuthenticationMode.PRIVATE_KEY_WITH_PASSPHRASE -> "Private key + passphrase"
                            },
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
                if (authMode != AuthenticationMode.PASSWORD) {
                    OutlinedButton(onClick = { keyPicker.launch(arrayOf("*/*")) }) {
                        Text(privateKeyName?.let { "Private key: $it" } ?: "Pilih private key")
                    }
                    if (authMode == AuthenticationMode.PRIVATE_KEY_WITH_PASSPHRASE && policy == CredentialPolicy.SAVE_SECURELY) {
                        OutlinedTextField(
                            keyPassphrase, { keyPassphrase = it }, label = { Text("Passphrase key (kosong = pertahankan)") },
                            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true,
                        )
                    }
                }
            }

            Text("Kredensial")
            Row {
                RadioButton(policy == CredentialPolicy.ALWAYS_ASK, onClick = {
                    policy = CredentialPolicy.ALWAYS_ASK
                    sshPassword = ""
                    vncPassword = ""
                    keyPassphrase = ""
                })
                Text("Selalu tanyakan", modifier = Modifier.padding(top = 12.dp))
            }
            Row {
                RadioButton(policy == CredentialPolicy.SAVE_SECURELY, onClick = { policy = CredentialPolicy.SAVE_SECURELY })
                Text("Simpan terenkripsi", modifier = Modifier.padding(top = 12.dp))
            }
            if (sshEnabled && authMode == AuthenticationMode.PASSWORD && policy == CredentialPolicy.SAVE_SECURELY) {
                OutlinedTextField(
                    sshPassword, { sshPassword = it }, label = { Text("Password SSH (kosong = pertahankan)") },
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true,
                )
            }

            OutlinedTextField(notes, { notes = it }, label = { Text("Catatan") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(favorite, onCheckedChange = { favorite = it })
                Text("Favorit")
            }
            error?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onCancel) { Text("Batal") }
                Button(onClick = {
                    val vp = vncPort.toIntOrNull() ?: 0
                    val sp = sshPort.toIntOrNull() ?: 0
                    if (name.isBlank() || host.isBlank() || (vncEnabled && vp !in 1..65535) || (sshEnabled && sp !in 1..65535) || (!vncEnabled && !sshEnabled)) {
                        error = "Periksa nama, host, protokol, dan port."
                        return@Button
                    }
                    validateCredentialRequirement(
                        isNew = initial == null,
                        previousAuthMode = initial?.authenticationMode,
                        sshEnabled = sshEnabled,
                        authMode = authMode,
                        policy = policy,
                        importedPrivateKey = privateKeyBytes != null,
                    )?.let { validationMessage ->
                        error = validationMessage
                        return@Button
                    }
                    val profile = (initial ?: ConnectionProfile.new(name, host, username)).copy(
                        name = name.trim(),
                        host = host.trim(),
                        username = username.trim(),
                        notes = notes,
                        favorite = favorite,
                        vncEnabled = vncEnabled,
                        vncPort = vp,
                        sshEnabled = sshEnabled,
                        sshPort = sp,
                        authenticationMode = authMode,
                        credentialPolicy = policy,
                    )
                    onSave(
                        profile,
                        sshPassword.takeIf { it.isNotEmpty() }?.toCharArray(),
                        vncPassword.takeIf { it.isNotEmpty() }?.toCharArray(),
                        privateKeyBytes?.copyOf(),
                        keyPassphrase.takeIf { it.isNotEmpty() }?.toCharArray(),
                    )
                    sshPassword = ""
                    vncPassword = ""
                    keyPassphrase = ""
                    privateKeyBytes?.fill(0)
                    privateKeyBytes = null
                }) { Text("Simpan") }
            }
        }
    }
}
