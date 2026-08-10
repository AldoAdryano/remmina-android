package com.remotex.core.security

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

internal object SecretCodec {
    fun encode(chars: CharArray): ByteArray {
        val encoder = StandardCharsets.UTF_8.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val encoded = encoder.encode(CharBuffer.wrap(chars))
        val result = ByteArray(encoded.remaining())
        encoded.get(result)
        if (encoded.hasArray()) encoded.array().fill(0)
        return result
    }

    fun decode(bytes: ByteArray): CharArray {
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val decoded = decoder.decode(ByteBuffer.wrap(bytes))
        val result = CharArray(decoded.remaining())
        decoded.get(result)
        if (decoded.hasArray()) decoded.array().fill('\u0000')
        return result
    }
}
