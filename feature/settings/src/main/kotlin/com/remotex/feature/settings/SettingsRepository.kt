package com.remotex.feature.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.remoteXSettings by preferencesDataStore(name = "remotex_settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

class SettingsRepository(
    private val context: Context,
) {
    private val appLockKey = booleanPreferencesKey("app_lock_enabled")
    private val themeModeKey = stringPreferencesKey("theme_mode")

    val appLockEnabled: Flow<Boolean> = context.remoteXSettings.data.map { it[appLockKey] ?: false }
    val themeMode: Flow<ThemeMode> = context.remoteXSettings.data.map { prefs ->
        runCatching { ThemeMode.valueOf(prefs[themeModeKey] ?: ThemeMode.SYSTEM.name) }
            .getOrDefault(ThemeMode.SYSTEM)
    }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        context.remoteXSettings.edit { it[appLockKey] = enabled }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.remoteXSettings.edit { it[themeModeKey] = mode.name }
    }
}
