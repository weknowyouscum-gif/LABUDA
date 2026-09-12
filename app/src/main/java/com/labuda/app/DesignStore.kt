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

internal const val KEY_DESIGN_CUSTOM = "design_custom"
internal const val KEY_DESIGN_BG = "design_bg"
internal const val KEY_DESIGN_TEXT = "design_text"
internal const val KEY_DESIGN_OUTLINE = "design_outline"

data class DesignPalette(
    val custom: Boolean = false,
    val bg: Long = 0xFF07060C,
    val text: Long = 0xFFF4F0FF,
    val outline: Long = 0xFF7A3DFF
)

object DesignStore {
    fun load(context: Context): DesignPalette {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return DesignPalette(
            custom = p.getBoolean(KEY_DESIGN_CUSTOM, false),
            bg = p.getLong(KEY_DESIGN_BG, 0xFF07060C),
            text = p.getLong(KEY_DESIGN_TEXT, 0xFFF4F0FF),
            outline = p.getLong(KEY_DESIGN_OUTLINE, 0xFF7A3DFF)
        )
    }
    fun save(context: Context, palette: DesignPalette) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_DESIGN_CUSTOM, palette.custom)
            .putLong(KEY_DESIGN_BG, palette.bg)
            .putLong(KEY_DESIGN_TEXT, palette.text)
            .putLong(KEY_DESIGN_OUTLINE, palette.outline)
            .apply()
    }
    fun reset(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_DESIGN_CUSTOM, false)
            .remove(KEY_DESIGN_BG).remove(KEY_DESIGN_TEXT).remove(KEY_DESIGN_OUTLINE)
            .apply()
    }
}

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
    val palette = DesignStore.load(context)
    val base = if (useDark) NeonDarkScheme else NeonLightScheme
    val scheme = if (!palette.custom) base else base.copy(
        background = Color(palette.bg),
        onBackground = Color(palette.text),
        surface = Color(palette.bg),
        onSurface = Color(palette.text),
        onSurfaceVariant = Color(palette.text).copy(alpha = 0.72f),
        outline = Color(palette.outline),
        outlineVariant = Color(palette.outline).copy(alpha = 0.45f)
    )
    MaterialTheme(colorScheme = scheme, typography = OswaldTypography, content = content)
}
