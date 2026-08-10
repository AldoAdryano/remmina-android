package com.remotex.feature.ssh.knownhosts

interface KnownHostRepository {
    suspend fun find(host: String, port: Int): KnownHostRecord?
    suspend fun save(record: KnownHostRecord)
    suspend fun delete(host: String, port: Int)
}
