package com.remotex.feature.ssh.terminal

object TerminalModifierEncoder {
    fun apply(input: ByteArray, ctrl: Boolean, alt: Boolean): ByteArray {
        if (input.isEmpty()) return input
        var body = input.copyOf()
        if (ctrl && body.size == 1) {
            val value = body[0].toInt() and 0xff
            val controlled = when (value) {
                in 'a'.code..'z'.code -> value - 'a'.code + 1
                in 'A'.code..'Z'.code -> value - 'A'.code + 1
                '@'.code, ' '.code -> 0
                '['.code -> 27
                '\\'.code -> 28
                ']'.code -> 29
                '^'.code -> 30
                '_'.code -> 31
                '?'.code -> 127
                else -> null
            }
            if (controlled != null) body[0] = controlled.toByte()
        }
        return if (alt) byteArrayOf(0x1b) + body else body
    }
}
