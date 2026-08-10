package com.remotex.core.database

import com.remotex.core.model.ConnectionProfile
import kotlinx.coroutines.flow.Flow

interface ProfileRepository {
    fun observeAll(): Flow<List<ConnectionProfile>>
    fun observeFavorites(): Flow<List<ConnectionProfile>>
    fun observeRecent(limit: Int = 20): Flow<List<ConnectionProfile>>
    suspend fun findById(id: Long): ConnectionProfile?
    suspend fun save(profile: ConnectionProfile): Long
    suspend fun delete(id: Long)
    suspend fun setFavorite(id: Long, favorite: Boolean)
    suspend fun markConnected(id: Long, at: Long = System.currentTimeMillis())
    suspend fun credentialRefs(id: Long): CredentialRefs?
    suspend fun updateCredentialRefs(id: Long, refs: CredentialRefs)
}

data class CredentialRefs(
    val passwordCredentialId: Long?,
    val vncPasswordCredentialId: Long?,
    val privateKeyCredentialId: Long?,
    val passphraseCredentialId: Long?,
)
