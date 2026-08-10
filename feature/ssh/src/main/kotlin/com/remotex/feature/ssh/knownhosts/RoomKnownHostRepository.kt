package com.remotex.feature.ssh.knownhosts

import com.remotex.core.database.KnownHostDao
import com.remotex.core.database.KnownHostEntity

class RoomKnownHostRepository(
    private val dao: KnownHostDao,
) : KnownHostRepository {
    override suspend fun find(host: String, port: Int): KnownHostRecord? = dao.find(host, port)?.let {
        KnownHostRecord(
            host = it.host,
            port = it.port,
            algorithm = it.algorithm,
            sha256Fingerprint = it.sha256Fingerprint,
            keyBlob = it.keyBlob,
            trustedAt = it.trustedAt,
        )
    }

    override suspend fun save(record: KnownHostRecord) {
        dao.upsert(
            KnownHostEntity(
                host = record.host,
                port = record.port,
                algorithm = record.algorithm,
                sha256Fingerprint = record.sha256Fingerprint,
                keyBlob = record.keyBlob,
                trustedAt = record.trustedAt,
            ),
        )
    }

    override suspend fun delete(host: String, port: Int) = dao.delete(host, port)
}
