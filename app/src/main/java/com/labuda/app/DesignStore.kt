package com.labuda.app

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

internal const val KEY_DESIGN_BG = "design_bg"
internal const val KEY_DESIGN_TEXT = "design_text"
internal const val KEY_DESIGN_OUTLINE = "design_outline"

data class DesignColors(
    val background: Color,
    val text: Color,
    val outline: Color
)

object DesignStore {
    val default = DesignColors(
        background = Color(0xFFF3F1F6),
        text = Color(0xFF1C1B1F),
        outline = Color(0xFFCAC4D0)
    )

    fun load(context: Context): DesignColors {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return DesignColors(
            background = Color(p.getInt(KEY_DESIGN_BG, default.background.toArgb())),
            text = Color(p.getInt(KEY_DESIGN_TEXT, default.text.toArgb())),
            outline = Color(p.getInt(KEY_DESIGN_OUTLINE, default.outline.toArgb()))
        )
    }

    fun save(context: Context, colors: DesignColors) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_DESIGN_BG, colors.background.toArgb())
            .putInt(KEY_DESIGN_TEXT, colors.text.toArgb())
            .putInt(KEY_DESIGN_OUTLINE, colors.outline.toArgb())
            .apply()
    }

    fun reset(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_DESIGN_BG).remove(KEY_DESIGN_TEXT).remove(KEY_DESIGN_OUTLINE).apply()
    }
}

fun ColorScheme.withDesign(design: DesignColors, dark: Boolean): ColorScheme {
    val faded = design.text.copy(alpha = 0.68f)
    return copy(
        background = design.background,
        surface = design.background,
        surfaceVariant = design.background,
        onBackground = design.text,
        onSurface = design.text,
        onSurfaceVariant = faded,
        outline = design.outline,
        outlineVariant = design.outline,
        primary = if (dark) design.text else design.outline,
        onPrimary = design.background
    )
}

@Composable
fun LabudaTheme(dark: Boolean = false, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var design by remember { mutableStateOf(DesignStore.load(context)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) design = DesignStore.load(context)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val base = if (dark) darkColorScheme() else lightColorScheme()
    MaterialTheme(
        colorScheme = base.withDesign(design, dark),
        typography = OswaldTypography,
        content = content
    )
}
