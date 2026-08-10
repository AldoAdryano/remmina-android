package com.remotex.feature.sftp

import com.remotex.feature.sftp.domain.RemoteFile
import com.remotex.feature.sftp.engine.RemoteSftpClient
import com.remotex.feature.ssh.domain.SftpTransport
import com.remotex.feature.ssh.domain.SftpTransportEntry
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SftpClientTest {
    private class FakeTransport : SftpTransport {
        val files = mutableMapOf<String, ByteArray>(
            "/home/user/a.txt" to "alpha".encodeToByteArray(),
            "/home/user/b.txt" to "beta".encodeToByteArray(),
            "/home/user/tree/one.txt" to "one".encodeToByteArray(),
            "/home/user/tree/nested/two.txt" to "two".encodeToByteArray(),
        )
        val dirs = mutableSetOf(
            "/", "/home", "/home/user", "/home/user/docs",
            "/home/user/tree", "/home/user/tree/nested",
        )

        override suspend fun list(path: String): List<SftpTransportEntry> {
            val prefix = if (path == "/") "/" else "$path/"
            val directories = dirs.asSequence()
                .filter { it != path && it.startsWith(prefix) && !it.removePrefix(prefix).contains('/') }
                .map { p -> SftpTransportEntry(p, p.substringAfterLast('/'), true, 0, null) }
            val regularFiles = files.asSequence()
                .filter { (p, _) -> p.startsWith(prefix) && !p.removePrefix(prefix).contains('/') }
                .map { (p, bytes) -> SftpTransportEntry(p, p.substringAfterLast('/'), false, bytes.size.toLong(), null) }
            return (directories + regularFiles).toList().reversed()
        }

        override suspend fun exists(path: String) = path in files || path in dirs
        override suspend fun mkdir(path: String) { dirs += path }

        override suspend fun rename(from: String, to: String) {
            files.remove(from)?.let { files[to] = it; return }
            if (from in dirs) {
                val dirEntries = dirs.filter { it == from || it.startsWith("$from/") }.sortedBy { it.length }
                val fileEntries = files.filterKeys { it.startsWith("$from/") }.toMap()
                dirs.removeAll(dirEntries.toSet())
                dirEntries.forEach { old -> dirs += to + old.removePrefix(from) }
                fileEntries.forEach { (old, bytes) ->
                    files.remove(old)
                    files[to + old.removePrefix(from)] = bytes
                }
                return
            }
            error("missing: $from")
        }

        override suspend fun removeFile(path: String) { files.remove(path) }
        override suspend fun removeDirectory(path: String) { dirs.remove(path) }
        override suspend fun openRead(path: String): InputStream = ByteArrayInputStream(files[path] ?: error("missing: $path"))
        override suspend fun openWrite(path: String, truncate: Boolean): OutputStream = object : ByteArrayOutputStream() {
            override fun close() {
                files[path] = toByteArray()
                super.close()
            }
        }
        override suspend fun close() = Unit
    }

    @Test
    fun list_sortsDirectoriesBeforeFilesAlphabetically() = runBlocking {
        val client = RemoteSftpClient(FakeTransport())
        val listed = client.list("/home/user")
        assertEquals(listOf("docs", "tree", "a.txt", "b.txt"), listed.map { it.name })
    }

    @Test
    fun prepareDestination_reportsConflicts() = runBlocking {
        val client = RemoteSftpClient(FakeTransport())
        assertTrue(client.prepareDestination("/home/user/a.txt").conflict)
        assertFalse(client.prepareDestination("/home/user/new.txt").conflict)
    }

    @Test
    fun copyAndMove_preserveFileContents() = runBlocking {
        val transport = FakeTransport()
        val client = RemoteSftpClient(transport)

        client.copy("/home/user/a.txt", "/home/user/copied.txt") { _, _ -> }
        assertEquals("alpha", transport.files["/home/user/copied.txt"]?.decodeToString())

        client.move("/home/user/b.txt", "/home/user/moved.txt")
        assertFalse("/home/user/b.txt" in transport.files)
        assertEquals("beta", transport.files["/home/user/moved.txt"]?.decodeToString())
    }

    @Test
    fun recursiveCopy_copiesNestedDirectoryTree() = runBlocking {
        val transport = FakeTransport()
        val client = RemoteSftpClient(transport)
        val source = RemoteFile("/home/user/tree", "tree", true, 0, null)

        client.copyPath(source, "/home/user/tree-copy") { _, _ -> }

        assertTrue("/home/user/tree-copy" in transport.dirs)
        assertTrue("/home/user/tree-copy/nested" in transport.dirs)
        assertEquals("one", transport.files["/home/user/tree-copy/one.txt"]?.decodeToString())
        assertEquals("two", transport.files["/home/user/tree-copy/nested/two.txt"]?.decodeToString())
    }

    @Test
    fun recursiveDelete_removesNestedDirectoryTree() = runBlocking {
        val transport = FakeTransport()
        val client = RemoteSftpClient(transport)
        val source = RemoteFile("/home/user/tree", "tree", true, 0, null)

        client.deletePath(source, recursive = true)

        assertFalse(transport.dirs.any { it == "/home/user/tree" || it.startsWith("/home/user/tree/") })
        assertFalse(transport.files.keys.any { it.startsWith("/home/user/tree/") })
    }
}
