package com.remotex.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    appLockEnabled: Boolean,
    themeMode: ThemeMode,
    onAppLockChanged: (Boolean) -> Unit,
    onThemeModeChanged: (ThemeMode) -> Unit,
    onExportProfiles: () -> Unit,
    onImportProfiles: () -> Unit,
    onClearLogs: () -> Unit,
) {
    Scaffold(topBar = { TopAppBar(title = { Text("Pengaturan") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(vertical = 8.dp)) {
            ListItem(
                headlineContent = { Text("Tema") },
                supportingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ThemeMode.entries.forEach { mode ->
                            RadioButton(selected = themeMode == mode, onClick = { onThemeModeChanged(mode) })
                            Text(
                                when (mode) {
                                    ThemeMode.SYSTEM -> "Sistem"
                                    ThemeMode.LIGHT -> "Terang"
                                    ThemeMode.DARK -> "Gelap"
                                }
                            )
                        }
                    }
                },
            )
            ListItem(
                headlineContent = { Text("Mode VNC default") },
                supportingContent = { Text("Trackpad • klik kanan 2 jari • Pas Layar") },
            )
            ListItem(
                headlineContent = { Text("Kunci aplikasi") },
                supportingContent = { Text("Opsional. Kredensial tetap terenkripsi walau fitur ini mati.") },
                trailingContent = { Switch(appLockEnabled, onCheckedChange = onAppLockChanged) },
            )
            TextButton(onClick = onExportProfiles, modifier = Modifier.fillMaxWidth()) { Text("Ekspor profil (tanpa password)") }
            TextButton(onClick = onImportProfiles, modifier = Modifier.fillMaxWidth()) { Text("Impor profil") }
            TextButton(onClick = onClearLogs, modifier = Modifier.fillMaxWidth()) { Text("Hapus log diagnostik") }
            Text("RemoteX V1 • VNC + SSH + SFTP", modifier = Modifier.padding(16.dp))
        }
    }
}
