package com.remotex.feature.ssh

import com.remotex.feature.ssh.domain.ShellChannel
import com.remotex.feature.ssh.terminal.SpecialKey
import com.remotex.feature.ssh.terminal.TerminalSessionController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeShell : ShellChannel {
    override val stdout: Flow<ByteArray> = emptyFlow()
    override val stderr: Flow<ByteArray> = emptyFlow()
    val writes = mutableListOf<ByteArray>()
    var resizedTo: Pair<Int, Int>? = null
    var closed = false

    override suspend fun write(bytes: ByteArray) { writes += bytes.copyOf() }
    override suspend fun resize(columns: Int, rows: Int) { resizedTo = columns to rows }
    override suspend fun close() { closed = true }
}

class TerminalControllerTest {
    @Test
    fun controller_writesResizesAndClosesShell() = runBlocking {
        val shell = FakeShell()
        val controller = TerminalSessionController(shell)
        controller.sendText("ls")
        controller.sendSpecialKey(SpecialKey.ENTER)
        controller.resize(columns = 120, rows = 40)
        assertEquals("ls", shell.writes[0].decodeToString())
        assertArrayEquals(byteArrayOf(0x0d), shell.writes[1])
        assertEquals(120 to 40, shell.resizedTo)
        controller.close()
        assertTrue(shell.closed)
    }
}
