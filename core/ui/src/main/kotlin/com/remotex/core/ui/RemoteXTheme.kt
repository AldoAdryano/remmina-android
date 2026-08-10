package com.remotex.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF54D6FF),
    secondary = Color(0xFF79E5F2),
    background = Color(0xFF07131E),
    surface = Color(0xFF0D1D2A),
    surfaceVariant = Color(0xFF132A3A),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF00668A),
    secondary = Color(0xFF006874),
    background = Color(0xFFF5FAFE),
    surface = Color(0xFFFFFFFF),
)

@Composable
fun RemoteXTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
