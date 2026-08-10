package com.remotex.feature.ssh

import com.remotex.feature.ssh.knownhosts.HostKeyDecision
import com.remotex.feature.ssh.knownhosts.KnownHostPolicy
import com.remotex.feature.ssh.knownhosts.KnownHostRecord
import com.remotex.feature.ssh.knownhosts.SshFingerprint
import com.remotex.feature.ssh.terminal.SpecialKey
import com.remotex.feature.ssh.terminal.SpecialKeyEncoder
import com.remotex.feature.ssh.terminal.TerminalModifierEncoder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SshCoreTest {
    @Test
    fun fingerprint_isOpenSshStyleSha256() {
        val fingerprint = SshFingerprint.sha256("public-key-material".encodeToByteArray())
        assertTrue(fingerprint.startsWith("SHA256:"))
        assertFalse(fingerprint.substringAfter(':').contains('='))
    }

    @Test
    fun hostKeyPolicy_distinguishesUnknownTrustedAndChanged() {
        val candidate = KnownHostRecord("server.local", 22, "ssh-ed25519", "SHA256:new", byteArrayOf(1, 2, 3), 2L)
        assertTrue(KnownHostPolicy.decide(null, candidate) is HostKeyDecision.Unknown)
        assertTrue(KnownHostPolicy.decide(candidate.copy(trustedAt = 1L), candidate) == HostKeyDecision.Trusted)
        val old = candidate.copy(sha256Fingerprint = "SHA256:old", keyBlob = byteArrayOf(9, 9, 9), trustedAt = 1L)
        assertTrue(KnownHostPolicy.decide(old, candidate) is HostKeyDecision.Changed)
    }

    @Test
    fun specialKeys_andModifiers_encodeCorrectly() {
        assertArrayEquals(byteArrayOf(0x0d), SpecialKeyEncoder.encode(SpecialKey.ENTER))
        assertArrayEquals(byteArrayOf(0x03), SpecialKeyEncoder.encode(SpecialKey.CTRL_C))
        assertArrayEquals(byteArrayOf(0x1b, 0x5b, 0x41), SpecialKeyEncoder.encode(SpecialKey.ARROW_UP))
        assertArrayEquals(byteArrayOf(0x03), TerminalModifierEncoder.apply("c".encodeToByteArray(), ctrl = true, alt = false))
        assertArrayEquals(byteArrayOf(0x1b, 0x78), TerminalModifierEncoder.apply("x".encodeToByteArray(), ctrl = false, alt = true))
    }
}
