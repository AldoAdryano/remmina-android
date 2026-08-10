package com.remotex.feature.vnc.protocol

import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object RfbAuth {
    fun challengeResponse(password: CharArray, challenge: ByteArray): ByteArray {
        require(challenge.size == 16) { "RFB VNC authentication challenge must be 16 bytes" }
        val key = ByteArray(8)
        for (index in 0 until minOf(8, password.size)) {
            key[index] = reverseBits(password[index].code and 0xff).toByte()
        }
        return try {
            val cipher = Cipher.getInstance("DES/ECB/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "DES"))
            cipher.doFinal(challenge)
        } finally {
            key.fill(0)
        }
    }

    private fun reverseBits(value: Int): Int {
        var input = value
        var output = 0
        repeat(8) {
            output = (output shl 1) or (input and 1)
            input = input ushr 1
        }
        return output
    }
}
