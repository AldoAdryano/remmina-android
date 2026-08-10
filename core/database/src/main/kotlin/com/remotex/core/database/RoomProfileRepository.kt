package com.remotex.core.database

import com.remotex.core.model.ConnectionProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomProfileRepository(
    private val dao: ProfileDao,
) : ProfileRepository {
    override fun observeAll(): Flow<List<ConnectionProfile>> = dao.observeAll().map { rows -> rows.map(ProfileEntity::toDomain) }
    override fun observeFavorites(): Flow<List<ConnectionProfile>> = dao.observeFavorites().map { rows -> rows.map(ProfileEntity::toDomain) }
    override fun observeRecent(limit: Int): Flow<List<ConnectionProfile>> = dao.observeRecent(limit).map { rows -> rows.map(ProfileEntity::toDomain) }
    override suspend fun findById(id: Long): ConnectionProfile? = dao.findById(id)?.toDomain()

    override suspend fun save(profile: ConnectionProfile): Long {
        val existing = if (profile.id == 0L) null else dao.findById(profile.id)
        return if (existing == null) {
            dao.insert(profile.toEntity())
        } else {
            dao.update(profile.toEntity(existing))
            profile.id
        }
    }

    override suspend fun delete(id: Long) = dao.deleteById(id)
    override suspend fun setFavorite(id: Long, favorite: Boolean) = dao.setFavorite(id, favorite)
    override suspend fun markConnected(id: Long, at: Long) = dao.markConnected(id, at)

    override suspend fun credentialRefs(id: Long): CredentialRefs? = dao.findById(id)?.let {
        CredentialRefs(it.passwordCredentialId, it.vncPasswordCredentialId, it.privateKeyCredentialId, it.passphraseCredentialId)
    }

    override suspend fun updateCredentialRefs(id: Long, refs: CredentialRefs) {
        val current = dao.findById(id) ?: return
        dao.update(
            current.copy(
                passwordCredentialId = refs.passwordCredentialId,
                vncPasswordCredentialId = refs.vncPasswordCredentialId,
                privateKeyCredentialId = refs.privateKeyCredentialId,
                passphraseCredentialId = refs.passphraseCredentialId,
            )
        )
    }
}
