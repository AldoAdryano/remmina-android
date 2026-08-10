package com.remotex.feature.vnc.screenshot

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.remotex.feature.vnc.domain.VncFrame
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VncScreenshotSaver(private val context: Context) {
    fun save(frame: VncFrame): Result<String> = runCatching {
        val name = "RemoteX-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())}.png"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveScoped(frame, name)
        } else {
            saveLegacyAppStorage(frame, name)
        }
        name
    }

    private fun saveScoped(frame: VncFrame, name: String) {
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
            resolver.openOutputStream(uri)?.use { output -> writePng(frame, output) }
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

    private fun saveLegacyAppStorage(frame: VncFrame, name: String) {
        val root = requireNotNull(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)) {
            "Penyimpanan gambar tidak tersedia"
        }
        val directory = File(root, "RemoteX").apply { mkdirs() }
        FileOutputStream(File(directory, name)).use { output -> writePng(frame, output) }
    }

    private fun writePng(frame: VncFrame, output: java.io.OutputStream) {
        val bitmap = Bitmap.createBitmap(frame.argb, frame.width, frame.height, Bitmap.Config.ARGB_8888)
        try {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "Gagal menulis PNG" }
        } finally {
            bitmap.recycle()
        }
    }
}
