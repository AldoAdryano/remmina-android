package com.remotex.android

import android.os.Build
import android.widget.Toast
import androidx.biometric.BiometricManager
import com.remotex.android.transfer.AndroidBackgroundTransferScheduler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.remotex.core.model.ConnectionProfile
import com.remotex.core.model.CredentialPolicy
import com.remotex.feature.connections.ConnectionEditorScreen
import com.remotex.feature.home.HomeScreen
import com.remotex.feature.settings.SettingsScreen
import com.remotex.feature.settings.ThemeMode
import com.remotex.feature.sftp.presentation.SftpScreen
import com.remotex.feature.sftp.presentation.SftpViewModel
import com.remotex.feature.ssh.domain.SshAuth
import com.remotex.feature.ssh.domain.SshConnectionSpec
import com.remotex.feature.ssh.domain.SshSessionState
import com.remotex.feature.ssh.presentation.SshTerminalScreen
import com.remotex.feature.ssh.presentation.SshViewModel
import com.remotex.feature.vnc.domain.VncSessionState
import com.remotex.feature.vnc.engine.RfbVncEngine
import com.remotex.feature.vnc.presentation.VncScreen
import com.remotex.feature.vnc.presentation.VncViewModel
import kotlinx.coroutines.launch

@Composable
fun RemoteXApp(container: AppContainer) {
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()
    val favorites by container.profileRepository.observeFavorites().collectAsState(initial = emptyList())
    val recent by container.profileRepository.observeRecent(20).collectAsState(initial = emptyList())
    val all by container.profileRepository.observeAll().collectAsState(initial = emptyList())

    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                favorites = favorites,
                recent = recent,
                allConnections = all,
                onAdd = { nav.navigate("edit/0") },
                onSettings = { nav.navigate("settings") },
                onQuickConnect = { nav.navigate("quick") },
                onVnc = { nav.navigate("vnc/$it") },
                onSsh = { nav.navigate("ssh/$it") },
                onSftp = { nav.navigate("sftp/$it") },
                onEdit = { nav.navigate("edit/$it") },
                onFavorite = { id, favorite -> scope.launch { container.profileRepository.setFavorite(id, favorite) } },
                onDelete = { id -> scope.launch { container.deleteProfile(id) } },
            )
        }

        composable(
            "edit/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: 0L
            val profile by produceState<ConnectionProfile?>(initialValue = null, id) {
                value = if (id == 0L) null else container.profileRepository.findById(id)
            }
            if (id != 0L && profile == null) {
                Loading()
            } else {
                ConnectionEditorScreen(
                    initial = profile,
                    onSave = { saved, sshPass, vncPass, key, passphrase ->
                        scope.launch {
                            container.saveProfile(saved, sshPass, vncPass, key, passphrase)
                            nav.popBackStack()
                        }
                    },
                    onCancel = { nav.popBackStack() },
                )
            }
        }

        composable("vnc/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            ProfileLoader(container, entry.arguments?.getLong("id") ?: 0L) { profile ->
                VncRoute(profile, container, onBack = { nav.popBackStack() })
            }
        }

        composable("ssh/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            ProfileLoader(container, entry.arguments?.getLong("id") ?: 0L) { profile ->
                SshRoute(profile, container, onBack = { nav.popBackStack() })
            }
        }

        composable("sftp/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            ProfileLoader(container, entry.arguments?.getLong("id") ?: 0L) { profile ->
                SftpRoute(profile, container, onBack = { nav.popBackStack() })
            }
        }

        composable("quick") {
            QuickConnectScreen(container, onBack = { nav.popBackStack() })
        }

        composable("settings") {
            SettingsRoute(container)
        }
    }
}

@Composable
private fun ProfileLoader(container: AppContainer, id: Long, content: @Composable (ConnectionProfile) -> Unit) {
    val profile by produceState<ConnectionProfile?>(initialValue = null, id) {
        value = container.profileRepository.findById(id)
    }
    val loadedProfile = profile
    if (loadedProfile != null) {
        content(loadedProfile)
    } else {
        Loading()
    }
}

@Composable
private fun VncRoute(profile: ConnectionProfile, container: AppContainer, onBack: () -> Unit) {
    val vm = remember(profile.id) { VncViewModel(RfbVncEngine()) }
    val state by vm.sessionState.collectAsState()
    var needsPrompt by remember(profile.id) { mutableStateOf(profile.credentialPolicy == CredentialPolicy.ALWAYS_ASK) }
    var attemptedSaved by remember(profile.id) { mutableStateOf(false) }

    DisposableEffect(vm) { onDispose { vm.disconnect() } }
    LaunchedEffect(profile.id, profile.credentialPolicy) {
        if (profile.credentialPolicy == CredentialPolicy.SAVE_SECURELY && !attemptedSaved) {
            attemptedSaved = true
            val password = container.savedVncPassword(profile.id)
            if (password == null) needsPrompt = true else vm.connect(profile.host, profile.vncPort, password)
        }
    }
    LaunchedEffect(state) {
        when (val currentState = state) {
            is VncSessionState.Connected -> {
                container.profileRepository.markConnected(profile.id)
                container.logger.event("vnc_connected", mapOf("profileId" to profile.id, "host" to profile.host, "port" to profile.vncPort))
            }
            is VncSessionState.Failed -> {
                container.logger.event("vnc_failed", mapOf("profileId" to profile.id, "reason" to currentState.reason))
            }
            else -> Unit
        }
    }

    if (needsPrompt) {
        PasswordPrompt("Password VNC • ${profile.name}") { password ->
            needsPrompt = false
            vm.connect(profile.host, profile.vncPort, password)
        }
    } else {
        VncScreen(vm, profile.name, onBack)
    }
}

@Composable
private fun SshRoute(profile: ConnectionProfile, container: AppContainer, onBack: () -> Unit) {
    val vm = remember(profile.id) { SshViewModel(container.newSshEngine()) }
    val scope = rememberCoroutineScope()
    val state by vm.state.collectAsState()
    var needsPrompt by remember(profile.id) { mutableStateOf(profile.credentialPolicy == CredentialPolicy.ALWAYS_ASK) }
    var started by remember(profile.id) { mutableStateOf(false) }

    DisposableEffect(vm) { onDispose { vm.disconnect() } }
    LaunchedEffect(profile.id, profile.credentialPolicy) {
        if (!started && profile.credentialPolicy == CredentialPolicy.SAVE_SECURELY) {
            val auth = container.savedSshAuth(profile)
            if (auth == null) needsPrompt = true else {
                started = true
                vm.connect(SshConnectionSpec(profile.host, profile.sshPort, profile.username, auth))
            }
        }
    }
    LaunchedEffect(state) {
        when (val currentState = state) {
            is SshSessionState.Connected -> {
                container.profileRepository.markConnected(profile.id)
                container.logger.event("ssh_connected", mapOf("profileId" to profile.id, "host" to profile.host, "port" to profile.sshPort))
            }
            is SshSessionState.Failed -> {
                container.logger.event("ssh_failed", mapOf("profileId" to profile.id, "reason" to currentState.reason))
            }
            else -> Unit
        }
    }

    if (needsPrompt) {
        SshAuthPrompt(profile.authenticationMode) { auth ->
            needsPrompt = false
            started = true
            vm.connect(SshConnectionSpec(profile.host, profile.sshPort, profile.username, auth))
        }
    } else {
        SshTerminalScreen(
            title = profile.name,
            viewModel = vm,
            onReconnect = {
                if (profile.credentialPolicy == CredentialPolicy.SAVE_SECURELY) {
                    scope.launch {
                        val auth = container.savedSshAuth(profile)
                        if (auth == null) needsPrompt = true
                        else vm.connect(SshConnectionSpec(profile.host, profile.sshPort, profile.username, auth))
                    }
                } else {
                    needsPrompt = true
                }
            },
            onBack = onBack,
        )
    }
}

@Composable
private fun SftpRoute(profile: ConnectionProfile, container: AppContainer, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val vm = remember(profile.id) { SftpViewModel(context.applicationContext, container.newSshEngine(), profile.id, AndroidBackgroundTransferScheduler(context.applicationContext), profile.credentialPolicy == CredentialPolicy.SAVE_SECURELY) }
    val state by vm.sshState.collectAsState()
    var needsPrompt by remember(profile.id) { mutableStateOf(profile.credentialPolicy == CredentialPolicy.ALWAYS_ASK) }
    var started by remember(profile.id) { mutableStateOf(false) }

    DisposableEffect(vm) { onDispose { vm.disconnect() } }
    LaunchedEffect(profile.id, profile.credentialPolicy) {
        if (!started && profile.credentialPolicy == CredentialPolicy.SAVE_SECURELY) {
            val auth = container.savedSshAuth(profile)
            if (auth == null) needsPrompt = true else {
                started = true
                vm.connect(SshConnectionSpec(profile.host, profile.sshPort, profile.username, auth))
            }
        }
    }
    LaunchedEffect(state) {
        when (val currentState = state) {
            is SshSessionState.Connected -> {
                container.profileRepository.markConnected(profile.id)
                container.logger.event("sftp_connected", mapOf("profileId" to profile.id, "host" to profile.host, "port" to profile.sshPort))
            }
            is SshSessionState.Failed -> {
                container.logger.event("sftp_failed", mapOf("profileId" to profile.id, "reason" to currentState.reason))
            }
            else -> Unit
        }
    }

    if (needsPrompt) {
        SshAuthPrompt(profile.authenticationMode) { auth ->
            needsPrompt = false
            started = true
            vm.connect(SshConnectionSpec(profile.host, profile.sshPort, profile.username, auth))
        }
    } else {
        SftpScreen(
            title = profile.name,
            viewModel = vm,
            onReconnect = {
                if (profile.credentialPolicy == CredentialPolicy.SAVE_SECURELY) {
                    scope.launch {
                        val auth = container.savedSshAuth(profile)
                        if (auth == null) needsPrompt = true
                        else vm.connect(SshConnectionSpec(profile.host, profile.sshPort, profile.username, auth))
                    }
                } else {
                    needsPrompt = true
                }
            },
            onBack = onBack,
        )
    }
}

@Composable
private fun SettingsRoute(container: AppContainer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appLock by container.settingsRepository.appLockEnabled.collectAsState(initial = false)
    val themeMode by container.settingsRepository.themeMode.collectAsState(initial = ThemeMode.SYSTEM)

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val json = ProfileJson.export(container.profileRepository)
                context.contentResolver.openOutputStream(uri, "w")?.bufferedWriter()?.use { it.write(json) }
                    ?: error("File ekspor tidak dapat dibuka")
            }.onSuccess {
                Toast.makeText(context, "Profil berhasil diekspor tanpa kredensial", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "Ekspor gagal: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("File impor tidak dapat dibuka")
                ProfileJson.import(container.profileRepository, json)
            }.onSuccess { count ->
                Toast.makeText(context, "$count profil berhasil diimpor", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "Impor gagal: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    SettingsScreen(
        appLockEnabled = appLock,
        themeMode = themeMode,
        onThemeModeChanged = { mode -> scope.launch { container.settingsRepository.setThemeMode(mode) } },
        onAppLockChanged = { enabled ->
            if (!enabled) {
                scope.launch { container.settingsRepository.setAppLockEnabled(false) }
            } else {
                val authenticators = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
                } else {
                    BiometricManager.Authenticators.BIOMETRIC_WEAK
                }
                if (BiometricManager.from(context).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS) {
                    scope.launch { container.settingsRepository.setAppLockEnabled(true) }
                } else {
                    Toast.makeText(context, "Kunci layar/biometrik perangkat belum tersedia", Toast.LENGTH_LONG).show()
                }
            }
        },
        onExportProfiles = { exportLauncher.launch("remotex-profiles.json") },
        onImportProfiles = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
        onClearLogs = {
            container.logStore.clearAll()
            Toast.makeText(context, "Log diagnostik dihapus", Toast.LENGTH_SHORT).show()
        },
    )
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}
