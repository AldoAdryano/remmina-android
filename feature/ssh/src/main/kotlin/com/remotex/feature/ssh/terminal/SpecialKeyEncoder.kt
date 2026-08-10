package com.remotex.feature.ssh.terminal

enum class SpecialKey {
    ENTER, TAB, ESC, CTRL_C, CTRL_D, CTRL_Z,
    ARROW_UP, ARROW_DOWN, ARROW_LEFT, ARROW_RIGHT,
}

object SpecialKeyEncoder {
    fun encode(key: SpecialKey): ByteArray = when (key) {
        SpecialKey.ENTER -> byteArrayOf('\r'.code.toByte())
        SpecialKey.TAB -> byteArrayOf('\t'.code.toByte())
        SpecialKey.ESC -> byteArrayOf(0x1b)
        SpecialKey.CTRL_C -> byteArrayOf(0x03)
        SpecialKey.CTRL_D -> byteArrayOf(0x04)
        SpecialKey.CTRL_Z -> byteArrayOf(0x1a)
        SpecialKey.ARROW_UP -> esc("[A")
        SpecialKey.ARROW_DOWN -> esc("[B")
        SpecialKey.ARROW_RIGHT -> esc("[C")
        SpecialKey.ARROW_LEFT -> esc("[D")
    }

    private fun esc(suffix: String): ByteArray = byteArrayOf(0x1b) + suffix.encodeToByteArray()
}
