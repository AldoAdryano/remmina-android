package com.remotex.feature.sftp.engine

import com.remotex.feature.sftp.domain.DestinationPreparation
import com.remotex.feature.sftp.domain.RemoteFile
import com.remotex.feature.ssh.domain.SftpTransport
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

class RemoteSftpClient(
    private val transport: SftpTransport,
) {
    suspend fun list(path: String): List<RemoteFile> = transport.list(path)
        .filterNot { it.name == "." || it.name == ".." }
        .map { RemoteFile(it.path, it.name, it.directory, it.size, it.modifiedAtEpochMillis) }
        .sortedWith(compareByDescending<RemoteFile> { it.directory }.thenBy { it.name.lowercase() })

    suspend fun prepareDestination(path: String): DestinationPreparation =
        DestinationPreparation(path, conflict = transport.exists(path))

    suspend fun mkdir(path: String) = transport.mkdir(path)

    suspend fun rename(from: String, to: String) = transport.rename(from, to)

    suspend fun move(from: String, to: String) = transport.rename(from, to)

    suspend fun deleteFile(path: String) = transport.removeFile(path)

    suspend fun deleteDirectory(path: String) = transport.removeDirectory(path)

    suspend fun copyPath(
        source: RemoteFile,
        destination: String,
        onProgress: (Long, Long?) -> Unit,
    ) {
        if (!source.directory) {
            copy(source.path, destination, onProgress)
            return
        }
        copyDirectory(source.path, destination, onProgress)
    }

    suspend fun deletePath(source: RemoteFile, recursive: Boolean = false) {
        if (!source.directory) {
            deleteFile(source.path)
            return
        }
        if (!recursive) {
            deleteDirectory(source.path)
            return
        }
        deleteDirectoryRecursive(source.path)
    }

    suspend fun copy(
        from: String,
        to: String,
        onProgress: (Long, Long?) -> Unit,
    ) = withContext(Dispatchers.IO) {
        if (transport.serverSideCopy(from, to)) return@withContext
        val total = runCatching {
            transport.list(parentOf(from)).firstOrNull { it.path == from }?.size
        }.getOrNull()
        transport.openRead(from).use { input ->
            transport.openWrite(to, truncate = true).use { output ->
                stream(input, output, total, onProgress)
            }
        }
    }

    suspend fun upload(
        input: InputStream,
        remotePath: String,
        totalBytes: Long?,
        onProgress: (Long, Long?) -> Unit,
    ) = withContext(Dispatchers.IO) {
        input.use { source ->
            transport.openWrite(remotePath, truncate = true).use { target ->
                stream(source, target, totalBytes, onProgress)
            }
        }
    }

    suspend fun download(
        remotePath: String,
        output: OutputStream,
        totalBytes: Long?,
        onProgress: (Long, Long?) -> Unit,
    ) = withContext(Dispatchers.IO) {
        transport.openRead(remotePath).use { source ->
            output.use { target -> stream(source, target, totalBytes, onProgress) }
        }
    }

    suspend fun close() = transport.close()

    private suspend fun stream(
        input: InputStream,
        output: OutputStream,
        total: Long?,
        onProgress: (Long, Long?) -> Unit,
    ) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var transferred = 0L
        while (true) {
            coroutineContext.ensureActive()
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            transferred += read
            onProgress(transferred, total)
        }
        output.flush()
    }


    private suspend fun copyDirectory(
        sourcePath: String,
        destinationPath: String,
        onProgress: (Long, Long?) -> Unit,
    ) {
        if (!transport.exists(destinationPath)) transport.mkdir(destinationPath)
        val children = list(sourcePath)
        var transferred = 0L
        val total = totalFileBytes(sourcePath)
        for (child in children) {
            coroutineContext.ensureActive()
            val childDestination = join(destinationPath, child.name)
            if (child.directory) {
                copyDirectory(child.path, childDestination) { done, _ ->
                    onProgress((transferred + done).coerceAtMost(total), total)
                }
                transferred += totalFileBytes(child.path)
            } else {
                copy(child.path, childDestination) { done, _ ->
                    onProgress((transferred + done).coerceAtMost(total), total)
                }
                transferred += child.size.coerceAtLeast(0)
            }
        }
        onProgress(transferred.coerceAtMost(total), total)
    }

    private suspend fun deleteDirectoryRecursive(path: String) {
        for (child in list(path)) {
            coroutineContext.ensureActive()
            if (child.directory) deleteDirectoryRecursive(child.path) else transport.removeFile(child.path)
        }
        transport.removeDirectory(path)
    }

    private suspend fun totalFileBytes(path: String): Long {
        var total = 0L
        for (child in list(path)) {
            total += if (child.directory) totalFileBytes(child.path) else child.size.coerceAtLeast(0)
        }
        return total
    }

    private fun join(parent: String, name: String): String = when {
        parent == "/" -> "/$name"
        parent.endsWith('/') -> parent + name
        else -> "$parent/$name"
    }

    private fun parentOf(path: String): String = path.substringBeforeLast('/', missingDelimiterValue = "/")
        .ifBlank { "/" }
}
