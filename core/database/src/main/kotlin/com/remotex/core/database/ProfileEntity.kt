package com.remotex.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.remotex.core.model.AuthenticationMode
import com.remotex.core.model.ConnectionProfile
import com.remotex.core.model.CredentialPolicy

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val host: String,
    val username: String,
    val notes: String,
    val favorite: Boolean,
    @ColumnInfo(name = "vnc_enabled") val vncEnabled: Boolean,
    @ColumnInfo(name = "vnc_port") val vncPort: Int,
    @ColumnInfo(name = "ssh_enabled") val sshEnabled: Boolean,
    @ColumnInfo(name = "ssh_port") val sshPort: Int,
    @ColumnInfo(name = "authentication_mode") val authenticationMode: String,
    @ColumnInfo(name = "credential_policy") val credentialPolicy: String,
    @ColumnInfo(name = "password_credential_id") val passwordCredentialId: Long?,
    @ColumnInfo(name = "vnc_password_credential_id") val vncPasswordCredentialId: Long?,
    @ColumnInfo(name = "private_key_credential_id") val privateKeyCredentialId: Long?,
    @ColumnInfo(name = "passphrase_credential_id") val passphraseCredentialId: Long?,
    @ColumnInfo(name = "last_connected_at") val lastConnectedAt: Long?,
)

fun ProfileEntity.toDomain(): ConnectionProfile = ConnectionProfile(
    id = id,
    name = name,
    host = host,
    username = username,
    notes = notes,
    favorite = favorite,
    vncEnabled = vncEnabled,
    vncPort = vncPort,
    sshEnabled = sshEnabled,
    sshPort = sshPort,
    authenticationMode = runCatching { AuthenticationMode.valueOf(authenticationMode) }.getOrDefault(AuthenticationMode.PASSWORD),
    credentialPolicy = runCatching { CredentialPolicy.valueOf(credentialPolicy) }.getOrDefault(CredentialPolicy.ALWAYS_ASK),
    lastConnectedAtEpochMillis = lastConnectedAt,
)

fun ConnectionProfile.toEntity(existing: ProfileEntity? = null): ProfileEntity = ProfileEntity(
    id = id,
    name = name,
    host = host,
    username = username,
    notes = notes,
    favorite = favorite,
    vncEnabled = vncEnabled,
    vncPort = vncPort,
    sshEnabled = sshEnabled,
    sshPort = sshPort,
    authenticationMode = authenticationMode.name,
    credentialPolicy = credentialPolicy.name,
    passwordCredentialId = existing?.passwordCredentialId,
    vncPasswordCredentialId = existing?.vncPasswordCredentialId,
    privateKeyCredentialId = existing?.privateKeyCredentialId,
    passphraseCredentialId = existing?.passphraseCredentialId,
    lastConnectedAt = lastConnectedAtEpochMillis,
)
