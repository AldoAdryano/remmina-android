package com.remotex.android

import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.remotex.core.ui.RemoteXTheme
import com.remotex.feature.settings.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    private val unlocked = mutableStateOf(false)

    private val container: AppContainer
        get() = (application as RemoteXApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val themeMode by container.settingsRepository.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            val systemDark = isSystemInDarkTheme()
            RemoteXTheme(
                darkTheme = when (themeMode) {
                    ThemeMode.SYSTEM -> systemDark
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                },
            ) {
                if (unlocked.value) {
                    RemoteXApp(container)
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("RemoteX terkunci")
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            if (!container.settingsRepository.appLockEnabled.first()) {
                unlocked.value = true
            } else if (!unlocked.value) {
                showUnlockPrompt()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        unlocked.value = false
    }

    private fun showUnlockPrompt() {
        val authenticators = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        }
        val manager = BiometricManager.from(this)
        if (manager.canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            unlocked.value = false
            return
        }
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    unlocked.value = true
                }
            },
        )
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Buka RemoteX")
            .setSubtitle("Autentikasi diperlukan untuk membuka profil remote")
            .setAllowedAuthenticators(authenticators)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) builder.setNegativeButtonText("Batal")
        prompt.authenticate(builder.build())
    }
}
