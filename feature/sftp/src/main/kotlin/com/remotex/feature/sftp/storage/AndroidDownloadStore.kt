package com.remotex.feature.sftp.storage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

data class LocalDownloadFile(
    val name: String,
    val uri: Uri,
    val size: Long?,
)

class AndroidDownloadStore(
    private val context: Context,
) {
    fun reserve(fileName: String, mimeType: String = "application/octet-stream"): Uri {
        val safeName = sanitize(fileName)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, safeName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/RemoteX")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            requireNotNull(context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)) {
                "Tidak dapat membuat file download"
            }
        } else {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "RemoteX").apply { mkdirs() }
            Uri.fromFile(uniqueFile(dir, safeName))
        }
    }

    fun open(uri: Uri): OutputStream = if (uri.scheme == "file") {
        FileOutputStream(requireNotNull(uri.path))
    } else {
        requireNotNull(context.contentResolver.openOutputStream(uri, "w")) { "Tidak dapat membuka file download" }
    }

    fun finish(uri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uri.scheme == "content") {
            val values = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
            context.contentResolver.update(uri, values, null, null)
        }
    }

    fun abort(uri: Uri) {
        if (uri.scheme == "file") {
            uri.path?.let { runCatching { File(it).delete() } }
        } else {
            runCatching { context.contentResolver.delete(uri, null, null) }
        }
    }

    fun create(fileName: String, mimeType: String = "application/octet-stream"): Pair<Uri, OutputStream> {
        val uri = reserve(fileName, mimeType)
        return uri to FinishingOutput(this, uri, open(uri))
    }

    fun list(): List<LocalDownloadFile> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "RemoteX")
            return dir.listFiles().orEmpty().filter { it.isFile }.sortedBy { it.name.lowercase() }.map {
                LocalDownloadFile(it.name, Uri.fromFile(it), it.length())
            }
        }
        val result = mutableListOf<LocalDownloadFile>()
        val projection = arrayOf(
            MediaStore.Downloads._ID,
            MediaStore.Downloads.DISPLAY_NAME,
            MediaStore.Downloads.SIZE,
        )
        val selection = "${MediaStore.Downloads.RELATIVE_PATH} LIKE ?"
        val args = arrayOf("${Environment.DIRECTORY_DOWNLOADS}/RemoteX%")
        context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            args,
            "${MediaStore.Downloads.DISPLAY_NAME} COLLATE NOCASE ASC",
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.SIZE)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                result += LocalDownloadFile(
                    name = cursor.getString(nameCol),
                    uri = Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id.toString()),
                    size = if (cursor.isNull(sizeCol)) null else cursor.getLong(sizeCol),
                )
            }
        }
        return result
    }

    private fun sanitize(name: String): String = name.substringAfterLast('/').replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "download.bin" }

    private fun uniqueFile(dir: File, requested: String): File {
        var candidate = File(dir, requested)
        if (!candidate.exists()) return candidate
        val base = requested.substringBeforeLast('.', requested)
        val ext = requested.substringAfterLast('.', "")
        var index = 1
        do {
            candidate = File(dir, if (ext.isBlank()) "$base ($index)" else "$base ($index).$ext")
            index++
        } while (candidate.exists())
        return candidate
    }
}

private class FinishingOutput(
    private val store: AndroidDownloadStore,
    private val uri: Uri,
    private val delegate: OutputStream,
) : OutputStream() {
    override fun write(b: Int) = delegate.write(b)
    override fun write(b: ByteArray, off: Int, len: Int) = delegate.write(b, off, len)
    override fun flush() = delegate.flush()
    override fun close() {
        delegate.close()
        store.finish(uri)
    }
}
