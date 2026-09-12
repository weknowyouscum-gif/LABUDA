package com.labuda.app

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun LabudaTheme(dark: Boolean? = null, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val useDark = dark ?: context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_DARK_THEME, false)
    MaterialTheme(
        colorScheme = if (useDark) darkColorScheme() else lightColorScheme(),
        typography = OswaldTypography,
        content = content
    )
}
