package com.remotex.core.security

import com.remotex.core.database.CredentialDao
import com.remotex.core.database.CredentialEntity

class RoomEncryptedCredentialStorage(
    private val dao: CredentialDao,
    private val now: () -> Long = System::currentTimeMillis,
) : EncryptedCredentialStorage {
    override suspend fun insert(kind: CredentialKind, payload: EncryptedPayload): Long {
        val timestamp = now()
        return dao.insert(
            CredentialEntity(
                kind = kind.name,
                ciphertext = payload.ciphertext,
                iv = payload.iv,
                createdAt = timestamp,
                updatedAt = timestamp,
            )
        )
    }

    override suspend fun find(id: Long): StoredCredential? = dao.findById(id)?.let { row ->
        val kind = runCatching { CredentialKind.valueOf(row.kind) }.getOrNull() ?: return@let null
        StoredCredential(
            id = row.id,
            kind = kind,
            payload = EncryptedPayload(row.ciphertext, row.iv),
        )
    }

    override suspend fun delete(id: Long) = dao.deleteById(id)
}
