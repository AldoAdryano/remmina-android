package com.remotex.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.remotex.core.model.ConnectionProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    favorites: List<ConnectionProfile>,
    recent: List<ConnectionProfile>,
    allConnections: List<ConnectionProfile>,
    onAdd: () -> Unit,
    onSettings: () -> Unit,
    onQuickConnect: () -> Unit,
    onVnc: (Long) -> Unit,
    onSsh: (Long) -> Unit,
    onSftp: (Long) -> Unit,
    onEdit: (Long) -> Unit,
    onFavorite: (Long, Boolean) -> Unit,
    onDelete: (Long) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("RemoteX") },
                actions = {
                    IconButton(onClick = onQuickConnect) {
                        Icon(Icons.Default.Bolt, contentDescription = "Koneksi cepat")
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Pengaturan")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = "Tambah koneksi")
            }
        },
    ) { padding ->
        if (allConnections.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text("Belum ada koneksi", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text("Tambahkan server pertama. Satu profil dapat dipakai untuk Desktop VNC, Terminal SSH, dan File SFTP.")
                Spacer(Modifier.height(20.dp))
                Button(onClick = onAdd) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text(" Tambah koneksi")
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (favorites.isNotEmpty()) {
                    item { SectionTitle("Favorit") }
                    items(favorites, key = { "fav-${it.id}" }) { profile ->
                        ConnectionCard(profile, onVnc, onSsh, onSftp, onEdit, onFavorite, onDelete)
                    }
                }
                if (recent.isNotEmpty()) {
                    item { SectionTitle("Terakhir digunakan") }
                    items(recent, key = { "recent-${it.id}" }) { profile ->
                        ConnectionCard(profile, onVnc, onSsh, onSftp, onEdit, onFavorite, onDelete)
                    }
                }
                item { SectionTitle("Semua koneksi") }
                items(allConnections, key = { "all-${it.id}" }) { profile ->
                    ConnectionCard(profile, onVnc, onSsh, onSftp, onEdit, onFavorite, onDelete)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun ConnectionCard(
    profile: ConnectionProfile,
    onVnc: (Long) -> Unit,
    onSsh: (Long) -> Unit,
    onSftp: (Long) -> Unit,
    onEdit: (Long) -> Unit,
    onFavorite: (Long, Boolean) -> Unit,
    onDelete: (Long) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(profile.name, style = MaterialTheme.typography.titleLarge)
                    Text(profile.host, style = MaterialTheme.typography.bodyMedium)
                    if (profile.username.isNotBlank()) Text(profile.username, style = MaterialTheme.typography.bodySmall)
                }
                Row {
                    IconButton(onClick = { onFavorite(profile.id, !profile.favorite) }) {
                        Icon(
                            if (profile.favorite) Icons.Default.Star else Icons.Outlined.StarBorder,
                            contentDescription = if (profile.favorite) "Hapus favorit" else "Jadikan favorit",
                        )
                    }
                    IconButton(onClick = { onEdit(profile.id) }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = { onDelete(profile.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Hapus")
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (profile.vncEnabled) {
                    FilledTonalButton(onClick = { onVnc(profile.id) }) {
                        Icon(Icons.Default.Computer, contentDescription = null)
                        Text(" Desktop")
                    }
                }
                if (profile.sshEnabled) {
                    FilledTonalButton(onClick = { onSsh(profile.id) }) {
                        Icon(Icons.Default.Terminal, contentDescription = null)
                        Text(" Terminal")
                    }
                    FilledTonalButton(onClick = { onSftp(profile.id) }) {
                        Icon(Icons.Default.Folder, contentDescription = null)
                        Text(" File")
                    }
                }
            }
        }
    }
}
