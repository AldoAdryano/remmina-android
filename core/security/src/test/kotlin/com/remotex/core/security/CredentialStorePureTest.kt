package com.remotex.core.security

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

private class ReversingCipher : CredentialCipher {
    override fun encrypt(plaintext: ByteArray): EncryptedPayload =
        EncryptedPayload(plaintext.reversedArray(), byteArrayOf(1, 2, 3))

    override fun decrypt(payload: EncryptedPayload): ByteArray = payload.ciphertext.reversedArray()
}

private class MemoryStorage : EncryptedCredentialStorage {
    private var nextId = 1L
    val rows = mutableMapOf<Long, StoredCredential>()

    override suspend fun insert(kind: CredentialKind, payload: EncryptedPayload): Long {
        val id = nextId++
        rows[id] = StoredCredential(id, kind, payload)
        return id
    }

    override suspend fun find(id: Long): StoredCredential? = rows[id]
    override suspend fun delete(id: Long) { rows.remove(id) }
}

class CredentialStoreTest {
    @Test
    fun encryptedStore_roundTripsWithoutMutatingCallerSecret() = runBlocking {
        val storage = MemoryStorage()
        val store = DatabaseCredentialStore(storage, ReversingCipher())
        val secret = charArrayOf('s', 'e', 'c', 'r', 'e', 't')
        val id = store.put(CredentialKind.PASSWORD, secret)

        assertEquals("secret", secret.concatToString())
        assertFalse(storage.rows[id]!!.payload.ciphertext.contentEquals("secret".encodeToByteArray()))

        val loaded = requireNotNull(store.read(id))
        assertEquals("secret", loaded.concatToString())
        loaded.fill('\u0000')

        store.delete(id)
        assertNull(storage.rows[id])
    }

    @Test
    fun byteCredentials_roundTrip() = runBlocking {
        val storage = MemoryStorage()
        val store = DatabaseCredentialStore(storage, ReversingCipher())
        val source = byteArrayOf(1, 2, 3, 4)
        val id = store.putBytes(CredentialKind.PRIVATE_KEY, source)
        assertArrayEquals(source, requireNotNull(store.readBytes(id)))
    }
}
