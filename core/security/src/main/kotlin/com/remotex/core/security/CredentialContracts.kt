package com.remotex.core.security

data class EncryptedPayload(
    val ciphertext: ByteArray,
    val iv: ByteArray,
)

enum class CredentialKind {
    PASSWORD,
    VNC_PASSWORD,
    PRIVATE_KEY,
    PRIVATE_KEY_PASSPHRASE,
}

data class StoredCredential(
    val id: Long,
    val kind: CredentialKind,
    val payload: EncryptedPayload,
)

interface CredentialCipher {
    fun encrypt(plaintext: ByteArray): EncryptedPayload
    fun decrypt(payload: EncryptedPayload): ByteArray
}

interface EncryptedCredentialStorage {
    suspend fun insert(kind: CredentialKind, payload: EncryptedPayload): Long
    suspend fun find(id: Long): StoredCredential?
    suspend fun delete(id: Long)
}

interface CredentialStore {
    /** The caller retains ownership of [secret]; this method never mutates it. */
    suspend fun put(kind: CredentialKind, secret: CharArray): Long
    suspend fun read(id: Long): CharArray?
    suspend fun delete(id: Long)

    /** The caller retains ownership of [bytes]; this method never mutates it. */
    suspend fun putBytes(kind: CredentialKind, bytes: ByteArray): Long
    suspend fun readBytes(id: Long): ByteArray?
}
