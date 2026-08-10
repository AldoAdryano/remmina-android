package com.remotex.android

import android.content.Context
import com.remotex.core.database.CredentialRefs
import com.remotex.core.database.ProfileRepository
import com.remotex.core.database.RemoteXDatabase
import com.remotex.core.database.RoomProfileRepository
import com.remotex.core.logging.AndroidLogStore
import com.remotex.core.logging.RedactingSafeLogger
import com.remotex.core.logging.SafeLogger
import com.remotex.core.model.AuthenticationMode
import com.remotex.core.model.ConnectionProfile
import com.remotex.core.model.CredentialPolicy
import com.remotex.core.security.AndroidKeystoreCredentialCipher
import com.remotex.core.security.CredentialKind
import com.remotex.core.security.CredentialStore
import com.remotex.core.security.DatabaseCredentialStore
import com.remotex.core.security.RoomEncryptedCredentialStorage
import com.remotex.feature.settings.SettingsRepository
import com.remotex.feature.ssh.domain.SshAuth
import com.remotex.feature.ssh.engine.MinaSshEngine
import com.remotex.feature.ssh.knownhosts.RoomKnownHostRepository

class AppContainer(context: Context) {
    val database: RemoteXDatabase = RemoteXDatabase.create(context)
    val profileRepository: ProfileRepository = RoomProfileRepository(database.profileDao())
    val credentialStore: CredentialStore = DatabaseCredentialStore(
        RoomEncryptedCredentialStorage(database.credentialDao()),
        AndroidKeystoreCredentialCipher(),
    )
    val knownHosts = RoomKnownHostRepository(database.knownHostDao())
    val settingsRepository = SettingsRepository(context.applicationContext)
    val logStore = AndroidLogStore(context.applicationContext).also { it.purgeExpired() }
    val logger: SafeLogger = RedactingSafeLogger(logStore)

    fun newSshEngine() = MinaSshEngine(knownHosts)

    suspend fun saveProfile(
        profile: ConnectionProfile,
        sshPassword: CharArray?,
        vncPassword: CharArray?,
        privateKey: ByteArray?,
        passphrase: CharArray?,
    ): Long {
        val id = profileRepository.save(profile)
        val current = profileRepository.credentialRefs(id) ?: CredentialRefs(null, null, null, null)

        if (profile.credentialPolicy == CredentialPolicy.ALWAYS_ASK) {
            deleteRefs(current)
            profileRepository.updateCredentialRefs(id, CredentialRefs(null, null, null, null))
            wipe(sshPassword, vncPassword, privateKey, passphrase)
            return id
        }

        var passwordId = current.passwordCredentialId
        var vncPasswordId = current.vncPasswordCredentialId
        var keyId = current.privateKeyCredentialId
        var passphraseId = current.passphraseCredentialId

        try {
            when (profile.authenticationMode) {
                AuthenticationMode.PASSWORD -> {
                    current.privateKeyCredentialId?.let { credentialStore.delete(it) }
                    current.passphraseCredentialId?.let { credentialStore.delete(it) }
                    keyId = null
                    passphraseId = null
                }
                AuthenticationMode.PRIVATE_KEY -> {
                    current.passwordCredentialId?.let { credentialStore.delete(it) }
                    current.passphraseCredentialId?.let { credentialStore.delete(it) }
                    passwordId = null
                    passphraseId = null
                }
                AuthenticationMode.PRIVATE_KEY_WITH_PASSPHRASE -> {
                    current.passwordCredentialId?.let { credentialStore.delete(it) }
                    passwordId = null
                }
            }
            if (sshPassword != null) {
                current.passwordCredentialId?.let { credentialStore.delete(it) }
                passwordId = credentialStore.put(CredentialKind.PASSWORD, sshPassword)
            }
            if (vncPassword != null) {
                current.vncPasswordCredentialId?.let { credentialStore.delete(it) }
                vncPasswordId = credentialStore.put(CredentialKind.VNC_PASSWORD, vncPassword)
            }
            if (privateKey != null) {
                current.privateKeyCredentialId?.let { credentialStore.delete(it) }
                keyId = credentialStore.putBytes(CredentialKind.PRIVATE_KEY, privateKey)
            }
            if (passphrase != null) {
                current.passphraseCredentialId?.let { credentialStore.delete(it) }
                passphraseId = credentialStore.put(CredentialKind.PRIVATE_KEY_PASSPHRASE, passphrase)
            }
            profileRepository.updateCredentialRefs(id, CredentialRefs(passwordId, vncPasswordId, keyId, passphraseId))
        } finally {
            wipe(sshPassword, vncPassword, privateKey, passphrase)
        }
        return id
    }

    suspend fun deleteProfile(id: Long) {
        profileRepository.credentialRefs(id)?.let { deleteRefs(it) }
        profileRepository.delete(id)
    }

    suspend fun savedVncPassword(profileId: Long): CharArray? {
        val id = profileRepository.credentialRefs(profileId)?.vncPasswordCredentialId ?: return null
        return credentialStore.read(id)
    }

    suspend fun savedSshAuth(profile: ConnectionProfile): SshAuth? {
        val refs = profileRepository.credentialRefs(profile.id) ?: return null
        return when (profile.authenticationMode) {
            AuthenticationMode.PASSWORD -> refs.passwordCredentialId?.let { credentialStore.read(it) }?.let(SshAuth::Password)
            AuthenticationMode.PRIVATE_KEY,
            AuthenticationMode.PRIVATE_KEY_WITH_PASSPHRASE -> {
                val key = refs.privateKeyCredentialId?.let { credentialStore.readBytes(it) } ?: return null
                val pass = refs.passphraseCredentialId?.let { credentialStore.read(it) }
                SshAuth.PrivateKey(key, pass)
            }
        }
    }

    private suspend fun deleteRefs(refs: CredentialRefs) {
        listOfNotNull(
            refs.passwordCredentialId,
            refs.vncPasswordCredentialId,
            refs.privateKeyCredentialId,
            refs.passphraseCredentialId,
        ).distinct().forEach { credentialStore.delete(it) }
    }

    private fun wipe(vararg values: Any?) {
        values.forEach {
            when (it) {
                is CharArray -> it.fill('\u0000')
                is ByteArray -> it.fill(0)
            }
        }
    }
}
