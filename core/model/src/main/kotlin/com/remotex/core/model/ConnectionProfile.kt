package com.remotex.core.model

enum class AuthenticationMode {
    PASSWORD,
    PRIVATE_KEY,
    PRIVATE_KEY_WITH_PASSPHRASE,
}

enum class CredentialPolicy {
    SAVE_SECURELY,
    ALWAYS_ASK,
}

data class ConnectionProfile(
    val id: Long,
    val name: String,
    val host: String,
    val username: String,
    val notes: String,
    val favorite: Boolean,
    val vncEnabled: Boolean,
    val vncPort: Int,
    val sshEnabled: Boolean,
    val sshPort: Int,
    val authenticationMode: AuthenticationMode,
    val credentialPolicy: CredentialPolicy,
    val lastConnectedAtEpochMillis: Long?,
) {
    companion object {
        fun new(
            name: String,
            host: String,
            username: String,
            notes: String = "",
            favorite: Boolean = false,
            vncEnabled: Boolean = true,
            vncPort: Int = 5900,
            sshEnabled: Boolean = true,
            sshPort: Int = 22,
            authenticationMode: AuthenticationMode = AuthenticationMode.PASSWORD,
            credentialPolicy: CredentialPolicy = CredentialPolicy.ALWAYS_ASK,
        ): ConnectionProfile = ConnectionProfile(
            id = 0,
            name = name.trim(),
            host = host.trim(),
            username = username.trim(),
            notes = notes,
            favorite = favorite,
            vncEnabled = vncEnabled,
            vncPort = vncPort,
            sshEnabled = sshEnabled,
            sshPort = sshPort,
            authenticationMode = authenticationMode,
            credentialPolicy = credentialPolicy,
            lastConnectedAtEpochMillis = null,
        )
    }
}

data class ValidationError(
    val field: String,
    val message: String,
)

class ConnectionValidator {
    fun validate(profile: ConnectionProfile): List<ValidationError> = buildList {
        if (profile.name.isBlank()) add(ValidationError("name", "Nama koneksi wajib diisi"))
        if (profile.host.isBlank()) add(ValidationError("host", "Host/IP wajib diisi"))
        if (profile.vncEnabled && profile.vncPort !in 1..65535) {
            add(ValidationError("vncPort", "Port VNC harus 1-65535"))
        }
        if (profile.sshEnabled && profile.sshPort !in 1..65535) {
            add(ValidationError("sshPort", "Port SSH harus 1-65535"))
        }
        if (!profile.vncEnabled && !profile.sshEnabled) {
            add(ValidationError("protocol", "Aktifkan VNC atau SSH/SFTP"))
        }
    }
}
