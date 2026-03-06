package com.viralclipai.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF6C63FF),
    onPrimary = Color.White,
    secondary = Color(0xFF8B83FF),
    background = Color(0xFF0F0F23),
    surface = Color(0xFF1A1A2E),
    onBackground = Color.White,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2A2A3E),
    onSurfaceVariant = Color(0xFF8888AA),
    error = Color(0xFFFF6B6B)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF6C63FF),
    onPrimary = Color.White,
    secondary = Color(0xFF8B83FF),
    background = Color(0xFFF8F8FF),
    surface = Color.White,
    onBackground = Color(0xFF1A1A2E),
    onSurface = Color(0xFF1A1A2E),
    surfaceVariant = Color(0xFFE8E8F0),
    onSurfaceVariant = Color(0xFF666688),
    error = Color(0xFFFF3B30)
)

@Composable
fun ViralClipTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(colorScheme = colorScheme, content = content)
}
