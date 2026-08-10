package com.remotex.feature.ssh

import com.remotex.feature.ssh.terminal.TerminalCommandHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalCommandHistoryTest {
    @Test
    fun history_isNewestFirstDeduplicatedAndBounded() {
        val history = TerminalCommandHistory(maxEntries = 3)
        history.record("ls -la")
        history.record("pwd")
        history.record("echo ok")
        history.record("whoami")
        assertEquals(listOf("whoami", "echo ok", "pwd"), history.entries)

        history.record("echo ok")
        assertEquals("echo ok", history.entries.first())
        assertEquals(3, history.entries.size)
    }

    @Test
    fun clear_removesAllEntries() {
        val history = TerminalCommandHistory()
        history.record("pwd")
        history.clear()
        assertTrue(history.entries.isEmpty())
    }
}
