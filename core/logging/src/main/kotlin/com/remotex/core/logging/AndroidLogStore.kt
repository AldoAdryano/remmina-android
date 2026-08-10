package com.remotex.core.logging

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AndroidLogStore(
    context: Context,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : LogSink {
    private val directory = File(context.applicationContext.filesDir, "diagnostics").apply { mkdirs() }
    private val dayFormat = SimpleDateFormat("yyyyMMdd", Locale.US)
    private val lock = Any()

    override fun write(line: String) {
        synchronized(lock) {
            runCatching {
                val file = File(directory, "remotex-${dayFormat.format(Date(nowMillis()))}.log")
                file.appendText(line.replace('\n', ' ').replace('\r', ' ') + "\n")
            }
        }
    }

    fun purgeExpired() {
        val cutoff = LogRetention.cutoff(nowMillis())
        synchronized(lock) {
            directory.listFiles().orEmpty().filter { it.isFile && it.lastModified() < cutoff }.forEach {
                runCatching { it.delete() }
            }
        }
    }

    fun clearAll() {
        synchronized(lock) {
            directory.listFiles().orEmpty().filter(File::isFile).forEach { runCatching { it.delete() } }
        }
    }

    fun files(): List<File> = synchronized(lock) {
        directory.listFiles().orEmpty().filter(File::isFile).sortedByDescending(File::lastModified)
    }
}
