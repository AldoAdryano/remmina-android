package com.remotex.feature.vnc.screenshot

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VncScreenshotSaver(private val context: Context) {
    fun save(bitmap: Bitmap): Result<String> = runCatching {
        val name = "RemoteX-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())}.png"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveScoped(bitmap, name)
        } else {
            saveLegacyAppStorage(bitmap, name)
        }
        name
    }

    private fun saveScoped(bitmap: Bitmap, name: String) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/RemoteX")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Tidak dapat membuat file screenshot")
        try {
            resolver.openOutputStream(uri)?.use { output -> writePng(bitmap, output) }
                ?: error("Tidak dapat membuka file screenshot")
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                null,
                null,
            )
        } catch (t: Throwable) {
            resolver.delete(uri, null, null)
            throw t
        }
    }

    private fun saveLegacyAppStorage(bitmap: Bitmap, name: String) {
        val root = requireNotNull(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)) {
            "Penyimpanan gambar tidak tersedia"
        }
        val directory = File(root, "RemoteX").apply { mkdirs() }
        FileOutputStream(File(directory, name)).use { output -> writePng(bitmap, output) }
    }

    private fun writePng(bitmap: Bitmap, output: java.io.OutputStream) {
        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "Gagal menulis PNG" }
    }
}
