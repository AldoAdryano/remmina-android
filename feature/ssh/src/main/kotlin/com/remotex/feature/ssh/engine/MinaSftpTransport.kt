package com.remotex.feature.ssh.engine

import com.remotex.feature.ssh.domain.SftpTransport
import com.remotex.feature.ssh.domain.SftpTransportEntry
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.sshd.sftp.client.SftpClient

internal class MinaSftpTransport(
    private val client: SftpClient,
) : SftpTransport {
    override suspend fun list(path: String): List<SftpTransportEntry> = withContext(Dispatchers.IO) {
        client.readDir(path)
            .asSequence()
            .filter { it.filename != "." && it.filename != ".." }
            .map { entry ->
                val attrs = entry.attributes
                SftpTransportEntry(
                    path = join(path, entry.filename),
                    name = entry.filename,
                    directory = attrs.isDirectory,
                    size = attrs.size,
                    modifiedAtEpochMillis = runCatching { attrs.modifyTime.toMillis() }.getOrNull(),
                )
            }
            .sortedWith(compareByDescending<SftpTransportEntry> { it.directory }.thenBy { it.name.lowercase() })
            .toList()
    }

    override suspend fun exists(path: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            client.stat(path)
            true
        }.getOrDefault(false)
    }

    override suspend fun mkdir(path: String) = withContext(Dispatchers.IO) {
        client.mkdir(path)
    }

    override suspend fun rename(from: String, to: String) = withContext(Dispatchers.IO) {
        client.rename(from, to)
    }

    override suspend fun removeFile(path: String) = withContext(Dispatchers.IO) {
        client.remove(path)
    }

    override suspend fun removeDirectory(path: String) = withContext(Dispatchers.IO) {
        client.rmdir(path)
    }

    override suspend fun openRead(path: String): InputStream = withContext(Dispatchers.IO) {
        client.read(path)
    }

    override suspend fun openWrite(path: String, truncate: Boolean): OutputStream = withContext(Dispatchers.IO) {
        if (truncate) {
            client.write(path, SftpClient.OpenMode.Write, SftpClient.OpenMode.Create, SftpClient.OpenMode.Truncate)
        } else {
            client.write(path, SftpClient.OpenMode.Write, SftpClient.OpenMode.Create, SftpClient.OpenMode.Append)
        }
    }

    override suspend fun close() = withContext(Dispatchers.IO) {
        client.close()
    }

    private fun join(parent: String, name: String): String = when {
        parent.isBlank() || parent == "/" -> "/$name"
        parent.endsWith('/') -> parent + name
        else -> "$parent/$name"
    }
}
