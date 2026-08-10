package com.remotex.feature.sftp.presentation

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.remotex.feature.sftp.domain.RemoteFile
import com.remotex.feature.sftp.domain.TransferState
import com.remotex.feature.sftp.engine.RemoteSftpClient
import com.remotex.feature.sftp.storage.AndroidDownloadStore
import com.remotex.feature.sftp.storage.LocalDownloadFile
import com.remotex.feature.sftp.transfer.BackgroundTransferDirection
import com.remotex.feature.sftp.transfer.BackgroundTransferHandle
import com.remotex.feature.sftp.transfer.BackgroundTransferRequest
import com.remotex.feature.sftp.transfer.BackgroundTransferScheduler
import com.remotex.feature.ssh.domain.SshAuth
import com.remotex.feature.ssh.domain.SshConnectionSpec
import com.remotex.feature.ssh.domain.SshEngine
import com.remotex.feature.ssh.domain.SshSessionHandle
import com.remotex.feature.ssh.domain.SshSessionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PendingUploadConflict(
    val uri: Uri,
    val displayName: String,
    val size: Long?,
    val destination: String,
)

data class SftpUiState(
    val path: String = ".",
    val files: List<RemoteFile> = emptyList(),
    val localFiles: List<LocalDownloadFile> = emptyList(),
    val loading: Boolean = false,
    val transferState: TransferState? = null,
    val transferInBackground: Boolean = false,
    val pendingUploadConflict: PendingUploadConflict? = null,
    val canRetryTransfer: Boolean = false,
    val error: String? = null,
)

class SftpViewModel(
    private val context: Context,
    private val engine: SshEngine,
    private val profileId: Long = 0,
    private val backgroundScheduler: BackgroundTransferScheduler? = null,
    private val backgroundAllowed: Boolean = false,
) : ViewModel() {
    val sshState: StateFlow<SshSessionState> = engine.state
    private val _uiState = MutableStateFlow(SftpUiState())
    val uiState: StateFlow<SftpUiState> = _uiState.asStateFlow()

    private val downloads = AndroidDownloadStore(context.applicationContext)
    private var pendingSpec: SshConnectionSpec? = null
    private var session: SshSessionHandle? = null
    private var client: RemoteSftpClient? = null
    private var transferJob: Job? = null
    private var backgroundHandle: BackgroundTransferHandle? = null
    private var backgroundDownloadUri: Uri? = null
    private var retryTransferAction: (() -> Unit)? = null

    fun connect(spec: SshConnectionSpec) {
        pendingSpec = spec
        viewModelScope.launch { connectInternal(spec) }
    }

    fun trustHostAndRetry() {
        val hostState = sshState.value as? SshSessionState.HostKeyRequired ?: return
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

    fun refresh() = viewModelScope.launch { load(_uiState.value.path) }

    fun openDirectory(file: RemoteFile) {
        if (!file.directory) return
        viewModelScope.launch { load(file.path) }
    }

    fun goParent() = viewModelScope.launch {
        val path = _uiState.value.path
        val parent = when {
            path == "." || path == "/" -> path
            else -> path.substringBeforeLast('/', missingDelimiterValue = ".").ifBlank { "/" }
        }
        load(parent)
    }

    fun createFolder(name: String) = viewModelScope.launch {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@launch
        runAction {
            clientOrThrow().mkdir(join(_uiState.value.path, trimmed))
            load(_uiState.value.path)
        }
    }

    fun rename(file: RemoteFile, newName: String) = viewModelScope.launch {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return@launch
        val destination = join(parentOf(file.path), trimmed)
        runAction {
            ensureDestinationFree(destination, file.path)
            clientOrThrow().rename(file.path, destination)
            load(_uiState.value.path)
        }
    }

    fun move(file: RemoteFile, destination: String) = viewModelScope.launch {
        val target = destination.trim()
        if (target.isEmpty()) return@launch
        runAction {
            ensureDestinationFree(target, file.path)
            clientOrThrow().move(file.path, target)
            load(_uiState.value.path)
        }
    }

    fun copy(file: RemoteFile, destination: String) {
        val target = destination.trim()
        if (target.isEmpty()) return
        retryTransferAction = { copy(file, target) }
        transferJob = viewModelScope.launch {
            runTransfer {
                ensureDestinationFree(target, file.path)
                clientOrThrow().copyPath(file, target) { done, total -> updateTransfer(done, total) }
                load(_uiState.value.path)
            }
        }
    }

    fun delete(file: RemoteFile, recursive: Boolean = false) = viewModelScope.launch {
        runAction {
            clientOrThrow().deletePath(file, recursive)
            load(_uiState.value.path)
        }
    }

    fun upload(uri: Uri, displayName: String, size: Long?) {
        viewModelScope.launch {
            val destination = join(_uiState.value.path, displayName)
            val conflict = runCatching { clientOrThrow().prepareDestination(destination).conflict }
                .onFailure { _uiState.value = _uiState.value.copy(error = safeMessage(it)) }
                .getOrNull() ?: return@launch
            if (conflict) {
                _uiState.value = _uiState.value.copy(
                    pendingUploadConflict = PendingUploadConflict(uri, displayName, size, destination),
                    error = null,
                )
            } else {
                startUpload(uri, displayName, size, destination)
            }
        }
    }

    fun confirmUploadOverwrite() {
        val pending = _uiState.value.pendingUploadConflict ?: return
        _uiState.value = _uiState.value.copy(pendingUploadConflict = null)
        startUpload(pending.uri, pending.displayName, pending.size, pending.destination)
    }

    fun dismissUploadConflict() {
        _uiState.value = _uiState.value.copy(pendingUploadConflict = null)
    }

    fun download(file: RemoteFile) {
        if (file.directory) return
        retryTransferAction = { download(file) }
        transferJob = viewModelScope.launch {
            val reserved = runCatching { downloads.reserve(file.name) }
                .onFailure { _uiState.value = _uiState.value.copy(error = safeMessage(it)) }
                .getOrNull() ?: return@launch
            val request = BackgroundTransferRequest(
                profileId,
                BackgroundTransferDirection.DOWNLOAD,
                reserved.toString(),
                file.path,
                file.name,
                file.size,
            )
            if (tryScheduleBackground(request, reserved)) return@launch
            runTransfer {
                try {
                    val output = downloads.open(reserved)
                    clientOrThrow().download(file.path, output, file.size) { done, total -> updateTransfer(done, total) }
                    downloads.finish(reserved)
                    refreshLocalFiles()
                } catch (t: Throwable) {
                    downloads.abort(reserved)
                    throw t
                }
            }
        }
    }

    fun cancelTransfer() {
        val handle = backgroundHandle
        if (handle != null) {
            backgroundScheduler?.cancel(handle)
            backgroundHandle = null
            backgroundDownloadUri?.let(downloads::abort)
            backgroundDownloadUri = null
        }
        transferJob?.cancel()
        transferJob = null
        _uiState.value = _uiState.value.copy(
            transferState = TransferState.Cancelled,
            transferInBackground = false,
            canRetryTransfer = retryTransferAction != null,
        )
    }

    fun retryTransfer() {
        val action = retryTransferAction ?: return
        backgroundHandle = null
        backgroundDownloadUri = null
        _uiState.value = _uiState.value.copy(error = null, transferInBackground = false)
        action()
    }

    fun disconnect() = viewModelScope.launch {
        transferJob?.cancel()
        client?.close()
        client = null
        session?.close()
        session = null
        engine.disconnect()
        clearPendingSecret()
    }

    private fun startUpload(uri: Uri, displayName: String, size: Long?, destination: String) {
        retryTransferAction = { startUpload(uri, displayName, size, destination) }
        transferJob = viewModelScope.launch {
            val request = BackgroundTransferRequest(
                profileId,
                BackgroundTransferDirection.UPLOAD,
                uri.toString(),
                destination,
                displayName,
                size,
            )
            if (tryScheduleBackground(request, null)) return@launch
            runTransfer {
                val input = requireNotNull(context.contentResolver.openInputStream(uri)) { "File lokal tidak dapat dibuka" }
                clientOrThrow().upload(input, destination, size) { done, total -> updateTransfer(done, total) }
                load(_uiState.value.path)
            }
        }
    }

    private fun tryScheduleBackground(request: BackgroundTransferRequest, downloadUri: Uri?): Boolean {
        if (!backgroundAllowed || profileId <= 0) return false
        val handle = backgroundScheduler?.schedule(request) ?: return false
        backgroundHandle = handle
        backgroundDownloadUri = downloadUri
        _uiState.value = _uiState.value.copy(
            transferState = TransferState.Queued,
            transferInBackground = true,
            canRetryTransfer = false,
            error = null,
        )
        return true
    }

    private suspend fun ensureDestinationFree(destination: String, source: String? = null) {
        if (destination == source) return
        if (clientOrThrow().prepareDestination(destination).conflict) {
            error("Tujuan sudah ada: $destination")
        }
    }

    private suspend fun connectInternal(spec: SshConnectionSpec) {
        _uiState.value = _uiState.value.copy(loading = true, error = null)
        val opened = engine.connect(spec) ?: run {
            _uiState.value = _uiState.value.copy(loading = false)
            if (sshState.value !is SshSessionState.HostKeyRequired) clearPendingSecret()
            return
        }
        session = opened
        client = RemoteSftpClient(opened.openSftpTransport())
        clearPendingSecret()
        refreshLocalFiles()
        load(".")
    }

    private suspend fun load(path: String) {
        runAction {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            val files = clientOrThrow().list(path)
            _uiState.value = _uiState.value.copy(path = path, files = files, loading = false)
        }
    }

    private fun refreshLocalFiles() {
        _uiState.value = _uiState.value.copy(localFiles = downloads.list())
    }

    private suspend fun runAction(block: suspend () -> Unit) {
        runCatching { block() }.onFailure {
            _uiState.value = _uiState.value.copy(loading = false, error = safeMessage(it))
        }
    }

    private suspend fun runTransfer(block: suspend () -> Unit) {
        backgroundHandle = null
        backgroundDownloadUri = null
        _uiState.value = _uiState.value.copy(
            transferState = TransferState.Queued,
            transferInBackground = false,
            canRetryTransfer = false,
            error = null,
        )
        try {
            block()
            _uiState.value = _uiState.value.copy(
                transferState = TransferState.Completed,
                canRetryTransfer = false,
            )
        } catch (_: CancellationException) {
            _uiState.value = _uiState.value.copy(
                transferState = TransferState.Cancelled,
                canRetryTransfer = retryTransferAction != null,
            )
        } catch (t: Throwable) {
            val message = safeMessage(t)
            _uiState.value = _uiState.value.copy(
                transferState = TransferState.Failed(message),
                canRetryTransfer = retryTransferAction != null,
                error = message,
            )
        }
    }

    private fun updateTransfer(done: Long, total: Long?) {
        _uiState.value = _uiState.value.copy(
            transferState = TransferState.Running(done, total, bytesPerSecond = 0),
            transferInBackground = false,
        )
    }

    private fun clientOrThrow(): RemoteSftpClient = requireNotNull(client) { "SFTP belum terhubung" }

    private fun safeMessage(t: Throwable): String = t.message?.take(240) ?: t::class.java.simpleName

    private fun join(parent: String, name: String): String = when {
        parent == "." -> "./$name"
        parent == "/" -> "/$name"
        parent.endsWith('/') -> parent + name
        else -> "$parent/$name"
    }

    private fun parentOf(path: String): String = path.substringBeforeLast('/', missingDelimiterValue = ".").ifBlank { "/" }

    private fun clearPendingSecret() {
        when (val auth = pendingSpec?.auth) {
            is SshAuth.Password -> auth.password.fill('\u0000')
            is SshAuth.PrivateKey -> {
                auth.keyBytes.fill(0)
                auth.passphrase?.fill('\u0000')
            }
            null -> Unit
        }
        pendingSpec = null
    }

    override fun onCleared() {
        transferJob?.cancel()
        viewModelScope.launch { disconnect() }
        super.onCleared()
    }
}
