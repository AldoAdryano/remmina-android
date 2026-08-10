package com.remotex.core.security

class DatabaseCredentialStore(
    private val storage: EncryptedCredentialStorage,
    private val cipher: CredentialCipher,
) : CredentialStore {
    override suspend fun put(kind: CredentialKind, secret: CharArray): Long {
        val encoded = SecretCodec.encode(secret)
        return try {
            val payload = cipher.encrypt(encoded)
            storage.insert(kind, payload)
        } finally {
            encoded.fill(0)
        }
    }

    override suspend fun read(id: Long): CharArray? {
        val row = storage.find(id) ?: return null
        val plaintext = cipher.decrypt(row.payload)
        return try {
            SecretCodec.decode(plaintext)
        } finally {
            plaintext.fill(0)
        }
    }

    override suspend fun delete(id: Long) = storage.delete(id)

    override suspend fun putBytes(kind: CredentialKind, bytes: ByteArray): Long {
        val workingCopy = bytes.copyOf()
        return try {
            storage.insert(kind, cipher.encrypt(workingCopy))
        } finally {
            workingCopy.fill(0)
        }
    }

    override suspend fun readBytes(id: Long): ByteArray? {
        val row = storage.find(id) ?: return null
        return cipher.decrypt(row.payload)
    }
}
