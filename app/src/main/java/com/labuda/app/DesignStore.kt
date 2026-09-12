package com.labuda.app

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val NeonBg = Color(0xFF07060C)
val NeonCard = Color(0xFF14121C)
val NeonCardAlt = Color(0xFF1C1828)
val NeonPurple = Color(0xFFB56BFF)
val NeonLine = Color(0xFF7A3DFF)
val NeonText = Color(0xFFF4F0FF)
val NeonMuted = Color(0xFFB7A8D4)

private val NeonDarkScheme = darkColorScheme(
    primary = NeonPurple,
    onPrimary = Color.White,
    secondary = NeonLine,
    background = NeonBg,
    onBackground = NeonText,
    surface = NeonCard,
    onSurface = NeonText,
    surfaceVariant = NeonCardAlt,
    onSurfaceVariant = NeonMuted,
    outline = NeonLine,
    outlineVariant = Color(0xFF3D2B66)
)

private val NeonLightScheme = lightColorScheme(
    primary = NeonLine,
    onPrimary = Color.White,
    secondary = NeonPurple,
    background = Color(0xFFF3EDFF),
    onBackground = Color(0xFF1A1228),
    surface = Color.White,
    onSurface = Color(0xFF1A1228),
    surfaceVariant = Color(0xFFE8DFF8),
    onSurfaceVariant = Color(0xFF4A3B66),
    outline = NeonLine,
    outlineVariant = Color(0xFFC9B6F0)
)

@Composable
fun LabudaTheme(dark: Boolean? = null, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val useDark = dark ?: context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_DARK_THEME, true)
    MaterialTheme(
        colorScheme = if (useDark) NeonDarkScheme else NeonLightScheme,
        typography = OswaldTypography,
        content = content
    )
}
