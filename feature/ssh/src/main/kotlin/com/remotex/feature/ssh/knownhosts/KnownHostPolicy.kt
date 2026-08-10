package com.remotex.feature.ssh.knownhosts

data class KnownHostRecord(
    val host: String,
    val port: Int,
    val algorithm: String,
    val sha256Fingerprint: String,
    val keyBlob: ByteArray,
    val trustedAt: Long,
)

sealed interface HostKeyDecision {
    data class Unknown(val candidate: KnownHostRecord) : HostKeyDecision
    data object Trusted : HostKeyDecision
    data class Changed(val old: KnownHostRecord, val candidate: KnownHostRecord) : HostKeyDecision
}

object KnownHostPolicy {
    fun decide(stored: KnownHostRecord?, candidate: KnownHostRecord): HostKeyDecision {
        if (stored == null) return HostKeyDecision.Unknown(candidate)
        return if (
            stored.algorithm == candidate.algorithm &&
            stored.keyBlob.contentEquals(candidate.keyBlob)
        ) {
            HostKeyDecision.Trusted
        } else {
            HostKeyDecision.Changed(stored, candidate)
        }
    }
}
