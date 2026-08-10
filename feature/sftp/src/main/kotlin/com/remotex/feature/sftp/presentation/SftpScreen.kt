package com.remotex.feature.sftp.presentation

import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.remotex.feature.sftp.domain.RemoteFile
import com.remotex.feature.sftp.domain.TransferState
import com.remotex.feature.sftp.storage.LocalDownloadFile
import com.remotex.feature.ssh.domain.SshSessionState

private enum class RemoteTextAction { RENAME, COPY, MOVE }
private data class RemoteTextRequest(val action: RemoteTextAction, val file: RemoteFile, val initial: String)

@Composable
fun SftpScreen(
    title: String,
    viewModel: SftpViewModel,
    onReconnect: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ui by viewModel.uiState.collectAsState()
    val ssh by viewModel.sshState.collectAsState()
    val context = LocalContext.current
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var newFolderDialog by remember { mutableStateOf(false) }
    var localSelected by remember { mutableStateOf(false) }
    var textRequest by remember { mutableStateOf<RemoteTextRequest?>(null) }
    var deleteTarget by remember { mutableStateOf<RemoteFile?>(null) }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            var name = "upload.bin"
            var size: Long? = null
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    name = cursor.getString(0) ?: name
                    size = if (cursor.isNull(1)) null else cursor.getLong(1)
                }
            }
            viewModel.upload(uri, name, size)
        }
    }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Row {
                IconButton(onClick = viewModel::goParent) { Icon(Icons.Default.KeyboardArrowUp, "Folder atas") }
                IconButton(onClick = viewModel::refresh) { Icon(Icons.Default.Refresh, "Muat ulang") }
                IconButton(onClick = { newFolderDialog = true }) { Icon(Icons.Default.CreateNewFolder, "Folder baru") }
                IconButton(onClick = { picker.launch(arrayOf("*/*")) }) { Icon(Icons.Default.Upload, "Unggah") }
                TextButton(onClick = { viewModel.disconnect(); onBack() }) { Text("Putus") }
            }
        }

        if (!landscape) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = { localSelected = true }) { Text("Lokal") }
                TextButton(onClick = { localSelected = false }) { Text("Remote") }
            }
        }

        val onRename: (RemoteFile) -> Unit = { file ->
            textRequest = RemoteTextRequest(RemoteTextAction.RENAME, file, file.name)
        }
        val onCopy: (RemoteFile) -> Unit = { file ->
            textRequest = RemoteTextRequest(RemoteTextAction.COPY, file, suggestedCopyPath(file.path))
        }
        val onMove: (RemoteFile) -> Unit = { file ->
            textRequest = RemoteTextRequest(RemoteTextAction.MOVE, file, file.path)
        }

        when {
            landscape -> Row(Modifier.weight(1f)) {
                LocalPane(ui.localFiles, Modifier.weight(0.42f))
                RemotePane(
                    ui.path,
                    ui.files,
                    viewModel::openDirectory,
                    viewModel::download,
                    onRename,
                    onCopy,
                    onMove,
                    { deleteTarget = it },
                    Modifier.weight(0.58f),
                )
            }
            localSelected -> LocalPane(ui.localFiles, Modifier.weight(1f))
            else -> RemotePane(
                ui.path,
                ui.files,
                viewModel::openDirectory,
                viewModel::download,
                onRename,
                onCopy,
                onMove,
                { deleteTarget = it },
                Modifier.weight(1f),
            )
        }

        ui.transferState?.let {
            TransferStatus(
                state = it,
                inBackground = ui.transferInBackground,
                canRetry = ui.canRetryTransfer,
                onCancel = viewModel::cancelTransfer,
                onRetry = viewModel::retryTransfer,
            )
        }
        ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp)) }
    }

    if (ui.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }

    val sshFailure = ssh as? SshSessionState.Failed
    if (sshFailure != null) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Koneksi SFTP terputus") },
            text = { Text(sshFailure.reason) },
            confirmButton = { Button(onClick = onReconnect) { Text("Sambungkan ulang") } },
            dismissButton = { OutlinedButton(onClick = { viewModel.disconnect(); onBack() }) { Text("Kembali") } },
        )
    }

    val hostKey = ssh as? SshSessionState.HostKeyRequired
    if (hostKey != null) {
        AlertDialog(
            onDismissRequest = viewModel::rejectHost,
            title = { Text(if (hostKey.previous == null) "Host SSH belum dikenal" else "Kunci host berubah") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (hostKey.previous != null) Text("Fingerprint lama: ${hostKey.previous?.sha256Fingerprint}")
                    Text("Fingerprint baru: ${hostKey.candidate.sha256Fingerprint}")
                }
            },
            confirmButton = { Button(onClick = viewModel::trustHostAndRetry) { Text("Percayai") } },
            dismissButton = { OutlinedButton(onClick = viewModel::rejectHost) { Text("Tolak") } },
        )
    }

    if (newFolderDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { newFolderDialog = false },
            title = { Text("Folder baru") },
            text = { OutlinedTextField(name, { name = it }, label = { Text("Nama folder") }) },
            confirmButton = {
                Button(onClick = { viewModel.createFolder(name); newFolderDialog = false }) { Text("Buat") }
            },
            dismissButton = { TextButton(onClick = { newFolderDialog = false }) { Text("Batal") } },
        )
    }

    ui.pendingUploadConflict?.let { conflict ->
        AlertDialog(
            onDismissRequest = viewModel::dismissUploadConflict,
            title = { Text("File tujuan sudah ada") },
            text = { Text("${conflict.destination}\n\nTimpa file remote dengan file yang dipilih?") },
            confirmButton = { Button(onClick = viewModel::confirmUploadOverwrite) { Text("Timpa") } },
            dismissButton = { TextButton(onClick = viewModel::dismissUploadConflict) { Text("Batal") } },
        )
    }

    textRequest?.let { request ->
        var value by remember(request) { mutableStateOf(request.initial) }
        val titleText = when (request.action) {
            RemoteTextAction.RENAME -> "Ganti nama"
            RemoteTextAction.COPY -> "Salin ke"
            RemoteTextAction.MOVE -> "Pindah ke"
        }
        AlertDialog(
            onDismissRequest = { textRequest = null },
            title = { Text(titleText) },
            text = {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text(if (request.action == RemoteTextAction.RENAME) "Nama baru" else "Path tujuan remote") },
                    singleLine = true,
                )
            },
            confirmButton = {
                Button(onClick = {
                    when (request.action) {
                        RemoteTextAction.RENAME -> viewModel.rename(request.file, value)
                        RemoteTextAction.COPY -> viewModel.copy(request.file, value)
                        RemoteTextAction.MOVE -> viewModel.move(request.file, value)
                    }
                    textRequest = null
                }) { Text("Jalankan") }
            },
            dismissButton = { TextButton(onClick = { textRequest = null }) { Text("Batal") } },
        )
    }

    deleteTarget?.let { file ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(if (file.directory) "Hapus folder?" else "Hapus file?") },
            text = {
                Text(
                    if (file.directory) {
                        "Folder ${file.name} beserta seluruh isinya akan dihapus dari server. Tindakan ini tidak dapat dibatalkan."
                    } else {
                        "File ${file.name} akan dihapus dari server. Tindakan ini tidak dapat dibatalkan."
                    }
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.delete(file, recursive = file.directory)
                    deleteTarget = null
                }) { Text("Hapus") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Batal") } },
        )
    }
}

@Composable
private fun LocalPane(files: List<LocalDownloadFile>, modifier: Modifier = Modifier) {
    Column(modifier.padding(8.dp)) {
        Text("Android • Downloads/RemoteX", style = MaterialTheme.typography.titleSmall)
        LazyColumn(Modifier.fillMaxSize()) {
            items(files, key = { it.uri.toString() }) { file ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.InsertDriveFile, null)
                    Column(Modifier.padding(start = 8.dp)) {
                        Text(file.name)
                        file.size?.let { Text("$it byte", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }
}

@Composable
private fun RemotePane(
    path: String,
    files: List<RemoteFile>,
    onDirectory: (RemoteFile) -> Unit,
    onDownload: (RemoteFile) -> Unit,
    onRename: (RemoteFile) -> Unit,
    onCopy: (RemoteFile) -> Unit,
    onMove: (RemoteFile) -> Unit,
    onDelete: (RemoteFile) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(8.dp)) {
        Text("Remote • $path", style = MaterialTheme.typography.titleSmall)
        LazyColumn(Modifier.fillMaxSize()) {
            items(files, key = { it.path }) { file ->
                var menuOpen by remember(file.path) { mutableStateOf(false) }
                Row(
                    Modifier.fillMaxWidth()
                        .clickable(enabled = file.directory) { onDirectory(file) }
                        .padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (file.directory) Icons.Default.Folder else Icons.Default.InsertDriveFile, null)
                    Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Text(file.name)
                        if (!file.directory) Text("${file.size} byte", style = MaterialTheme.typography.bodySmall)
                    }
                    if (!file.directory) {
                        IconButton(onClick = { onDownload(file) }) { Icon(Icons.Default.Download, "Unduh") }
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "Aksi file") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Ganti nama") },
                                leadingIcon = { Icon(Icons.Default.Edit, null) },
                                onClick = { menuOpen = false; onRename(file) },
                            )
                            DropdownMenuItem(
                                text = { Text("Salin") },
                                leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                                onClick = { menuOpen = false; onCopy(file) },
                            )
                            DropdownMenuItem(
                                text = { Text("Pindah") },
                                leadingIcon = { Icon(Icons.Default.DriveFileMove, null) },
                                onClick = { menuOpen = false; onMove(file) },
                            )
                            DropdownMenuItem(
                                text = { Text("Hapus") },
                                leadingIcon = { Icon(Icons.Default.Delete, null) },
                                onClick = { menuOpen = false; onDelete(file) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransferStatus(
    state: TransferState,
    inBackground: Boolean,
    canRetry: Boolean,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    val label = when (state) {
        TransferState.Queued -> if (inBackground) "Transfer berjalan di background" else "Transfer menunggu"
        is TransferState.Running -> if (state.totalBytes != null && state.totalBytes > 0) {
            "Transfer ${state.transferredBytes}/${state.totalBytes} byte"
        } else {
            "Transfer ${state.transferredBytes} byte"
        }
        TransferState.Completed -> "Transfer selesai"
        is TransferState.Failed -> "Transfer gagal: ${state.message}"
        TransferState.Cancelled -> "Transfer dibatalkan"
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        if (state == TransferState.Queued || state is TransferState.Running) {
            IconButton(onClick = onCancel) { Icon(Icons.Default.Cancel, "Batalkan transfer") }
        }
        if (canRetry && (state is TransferState.Failed || state == TransferState.Cancelled)) {
            IconButton(onClick = onRetry) { Icon(Icons.Default.Replay, "Ulangi transfer") }
        }
    }
}

private fun suggestedCopyPath(path: String): String {
    val parent = path.substringBeforeLast('/', missingDelimiterValue = ".")
    val name = path.substringAfterLast('/')
    return if (parent == "/") "/$name-copy" else "$parent/$name-copy"
}
