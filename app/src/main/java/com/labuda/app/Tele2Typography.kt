package com.labuda.app

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

val OswaldText = FontFamily(
    Font(R.font.oswald, FontWeight.Normal),
)

private fun oswald(style: TextStyle): TextStyle = style.copy(fontFamily = OswaldText)

val OswaldTypography: Typography by lazy {
    val base = Typography()
    Typography(
        displayLarge = oswald(base.displayLarge),
        displayMedium = oswald(base.displayMedium),
        displaySmall = oswald(base.displaySmall),
        headlineLarge = oswald(base.headlineLarge),
        headlineMedium = oswald(base.headlineMedium),
        headlineSmall = oswald(base.headlineSmall),
        titleLarge = oswald(base.titleLarge),
        titleMedium = oswald(base.titleMedium),
        titleSmall = oswald(base.titleSmall),
        bodyLarge = oswald(base.bodyLarge),
        bodyMedium = oswald(base.bodyMedium),
        bodySmall = oswald(base.bodySmall),
        labelLarge = oswald(base.labelLarge),
        labelMedium = oswald(base.labelMedium),
        labelSmall = oswald(base.labelSmall),
    )
}
