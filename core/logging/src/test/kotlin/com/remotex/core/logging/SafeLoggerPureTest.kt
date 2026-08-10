package com.remotex.core.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeLoggerTest {
    @Test
    fun logger_redactsSensitiveValues() {
        val sink = RecordingLogSink()
        val logger = RedactingSafeLogger(sink)
        logger.event(
            "ssh_connect_failed",
            mapOf(
                "host" to "server.local",
                "password" to "hunter2",
                "clipboard" to "secret-text",
                "reason" to "timeout",
            ),
        )
        val output = sink.lines.joinToString("\n")
        assertFalse(output.contains("hunter2"))
        assertFalse(output.contains("secret-text"))
        assertTrue(output.contains("[REDACTED]"))
        assertTrue(output.contains("server.local"))
    }

    @Test
    fun retention_isSevenDays() {
        assertEquals(3L * 24 * 60 * 60 * 1000, LogRetention.cutoff(10L * 24 * 60 * 60 * 1000))
    }
}
