package com.remotex.feature.connections

import com.remotex.core.model.AuthenticationMode
import com.remotex.core.model.CredentialPolicy

fun validateCredentialRequirement(
    isNew: Boolean,
    previousAuthMode: AuthenticationMode?,
    sshEnabled: Boolean,
    authMode: AuthenticationMode,
    policy: CredentialPolicy,
    importedPrivateKey: Boolean,
): String? {
    if (!sshEnabled || policy != CredentialPolicy.SAVE_SECURELY || authMode == AuthenticationMode.PASSWORD) {
        return null
    }

    val canRetainExistingKey = !isNew && previousAuthMode != AuthenticationMode.PASSWORD
    return if (importedPrivateKey || canRetainExistingKey) {
        null
    } else {
        "Pilih private key untuk disimpan terenkripsi."
    }
}
