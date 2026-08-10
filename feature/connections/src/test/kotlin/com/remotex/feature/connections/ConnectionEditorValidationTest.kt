package com.remotex.feature.connections

import com.remotex.core.model.AuthenticationMode
import com.remotex.core.model.CredentialPolicy
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ConnectionEditorValidationTest {
    @Test
    fun newSavedPrivateKeyProfile_requiresImportedKey() {
        assertNotNull(
            validateCredentialRequirement(
                isNew = true,
                previousAuthMode = null,
                sshEnabled = true,
                authMode = AuthenticationMode.PRIVATE_KEY,
                policy = CredentialPolicy.SAVE_SECURELY,
                importedPrivateKey = false,
            )
        )
    }

    @Test
    fun existingPrivateKeyProfile_canRetainStoredKey() {
        assertNull(
            validateCredentialRequirement(
                isNew = false,
                previousAuthMode = AuthenticationMode.PRIVATE_KEY,
                sshEnabled = true,
                authMode = AuthenticationMode.PRIVATE_KEY,
                policy = CredentialPolicy.SAVE_SECURELY,
                importedPrivateKey = false,
            )
        )
    }

    @Test
    fun switchingFromPasswordToSavedPrivateKey_requiresImportedKey() {
        assertNotNull(
            validateCredentialRequirement(
                isNew = false,
                previousAuthMode = AuthenticationMode.PASSWORD,
                sshEnabled = true,
                authMode = AuthenticationMode.PRIVATE_KEY,
                policy = CredentialPolicy.SAVE_SECURELY,
                importedPrivateKey = false,
            )
        )
    }

    @Test
    fun alwaysAskPrivateKey_doesNotRequireSavedKey() {
        assertNull(
            validateCredentialRequirement(
                isNew = true,
                previousAuthMode = null,
                sshEnabled = true,
                authMode = AuthenticationMode.PRIVATE_KEY,
                policy = CredentialPolicy.ALWAYS_ASK,
                importedPrivateKey = false,
            )
        )
    }
}
