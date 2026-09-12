package com.labuda.app

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

internal const val KEY_THEME = "design_theme"
const val THEME_NEON = "neon"
const val THEME_BORDO = "bordo"
const val THEME_ORANGE = "orange"

object DesignStore {
    fun themeId(context: Context): String {
        val id = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_THEME, THEME_NEON).orEmpty()
        return if (id == THEME_BORDO || id == THEME_ORANGE) id else THEME_NEON
    }
    fun save(context: Context, themeId: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_THEME, themeId).apply()
    }
    fun reset(context: Context) = save(context, THEME_NEON)
}

private fun scheme(themeId: String, dark: Boolean) = when (themeId) {
    THEME_BORDO -> if (dark) darkColorScheme(
        primary = Color(0xFFC43B5A),
        onPrimary = Color.White,
        secondary = Color(0xFF8B1E3F),
        background = Color(0xFF0A0507),
        onBackground = Color(0xFFF6E8EC),
        surface = Color(0xFF14080C),
        onSurface = Color(0xFFF6E8EC),
        surfaceVariant = Color(0xFF1C0C12),
        onSurfaceVariant = Color(0xFFC9A3AD),
        outline = Color(0xFF8B1E3F),
        outlineVariant = Color(0xFF4A1828)
    ) else lightColorScheme(
        primary = Color(0xFF8B1E3F),
        onPrimary = Color.White,
        secondary = Color(0xFFC43B5A),
        background = Color(0xFFF7ECEF),
        onBackground = Color(0xFF2A1016),
        surface = Color.White,
        onSurface = Color(0xFF2A1016),
        surfaceVariant = Color(0xFFF0DCE2),
        onSurfaceVariant = Color(0xFF6A3A46),
        outline = Color(0xFF8B1E3F),
        outlineVariant = Color(0xFFE0B8C2)
    )
    THEME_ORANGE -> if (dark) darkColorScheme(
        primary = Color(0xFFFF6A00),
        onPrimary = Color.White,
        secondary = Color(0xFFE85D04),
        background = Color(0xFF0A0A0A),
        onBackground = Color(0xFFFFF4EA),
        surface = Color(0xFF141414),
        onSurface = Color(0xFFFFF4EA),
        surfaceVariant = Color(0xFF1C1612),
        onSurfaceVariant = Color(0xFFC9B8A8),
        outline = Color(0xFFE85D04),
        outlineVariant = Color(0xFF5A3010)
    ) else lightColorScheme(
        primary = Color(0xFFE85D04),
        onPrimary = Color.White,
        secondary = Color(0xFFFF6A00),
        background = Color(0xFFFFF4EA),
        onBackground = Color(0xFF2A1608),
        surface = Color.White,
        onSurface = Color(0xFF2A1608),
        surfaceVariant = Color(0xFFFFE4CC),
        onSurfaceVariant = Color(0xFF6A4A30),
        outline = Color(0xFFE85D04),
        outlineVariant = Color(0xFFF0C8A0)
    )
    else -> if (dark) darkColorScheme(
        primary = Color(0xFFB56BFF),
        onPrimary = Color.White,
        secondary = Color(0xFF7A3DFF),
        background = Color(0xFF07060C),
        onBackground = Color(0xFFF4F0FF),
        surface = Color(0xFF14121C),
        onSurface = Color(0xFFF4F0FF),
        surfaceVariant = Color(0xFF1C1828),
        onSurfaceVariant = Color(0xFFB7A8D4),
        outline = Color(0xFF7A3DFF),
        outlineVariant = Color(0xFF3D2B66)
    ) else lightColorScheme(
        primary = Color(0xFF7A3DFF),
        onPrimary = Color.White,
        secondary = Color(0xFFB56BFF),
        background = Color(0xFFF3EDFF),
        onBackground = Color(0xFF1A1228),
        surface = Color.White,
        onSurface = Color(0xFF1A1228),
        surfaceVariant = Color(0xFFE8DFF8),
        onSurfaceVariant = Color(0xFF4A3B66),
        outline = Color(0xFF7A3DFF),
        outlineVariant = Color(0xFFC9B6F0)
    )
}

@Composable
fun LabudaTheme(dark: Boolean? = null, themeId: String? = null, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val useDark = dark ?: context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_DARK_THEME, true)
    val id = themeId ?: DesignStore.themeId(context)
    MaterialTheme(colorScheme = scheme(id, useDark), typography = OswaldTypography, content = content)
}
