package com.remotex.feature.ssh.knownhosts

import java.security.MessageDigest
import java.util.Base64

object SshFingerprint {
    fun sha256(keyBlob: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(keyBlob)
        val value = Base64.getEncoder().withoutPadding().encodeToString(digest)
        digest.fill(0)
        return "SHA256:$value"
    }
}
