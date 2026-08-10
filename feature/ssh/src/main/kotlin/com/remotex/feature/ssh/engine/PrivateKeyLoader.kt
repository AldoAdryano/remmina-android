package com.remotex.feature.ssh.engine

import java.io.ByteArrayInputStream
import java.security.KeyPair
import org.apache.sshd.common.NamedResource
import org.apache.sshd.common.config.keys.FilePasswordProvider
import org.apache.sshd.common.util.security.SecurityUtils

object PrivateKeyLoader {
    fun load(keyBytes: ByteArray, passphrase: CharArray?): List<KeyPair> {
        require(keyBytes.isNotEmpty()) { "Private key kosong" }
        val provider = passphrase?.let { FilePasswordProvider.of(it.concatToString()) }
        return ByteArrayInputStream(keyBytes).use { input ->
            SecurityUtils.loadKeyPairIdentities(
                null,
                NamedResource.ofName("remotex-memory-key"),
                input,
                provider,
            )?.toList().orEmpty()
        }
    }
}
