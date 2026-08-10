package com.remotex.core.logging

interface LogSink {
    fun write(line: String)
}

class RecordingLogSink : LogSink {
    val lines = mutableListOf<String>()
    override fun write(line: String) {
        lines += line
    }
}

interface SafeLogger {
    fun event(name: String, fields: Map<String, Any?> = emptyMap())
}

class RedactingSafeLogger(
    private val sink: LogSink,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : SafeLogger {
    private val sensitiveKeys = setOf(
        "password",
        "passphrase",
        "privatekey",
        "private_key",
        "credential",
        "ciphertext",
        "clipboard",
    )

    override fun event(name: String, fields: Map<String, Any?>) {
        val payload = fields.entries.joinToString(separator = " ") { (key, value) ->
            val rendered = if (key.lowercase() in sensitiveKeys) "[REDACTED]" else value.toString()
            "$key=$rendered"
        }
        sink.write("${nowMillis()} $name${if (payload.isBlank()) "" else " $payload"}")
    }
}

object LogRetention {
    private const val RETENTION_MS = 7L * 24 * 60 * 60 * 1000
    fun cutoff(nowMillis: Long): Long = nowMillis - RETENTION_MS
}
