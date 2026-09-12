package com.labuda.app

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val OswaldText = FontFamily(
    Font(R.font.oswald, FontWeight.Thin),
    Font(R.font.oswald, FontWeight.Light),
    Font(R.font.oswald, FontWeight.Normal),
    Font(R.font.oswald, FontWeight.Medium),
    Font(R.font.oswald, FontWeight.SemiBold),
    Font(R.font.oswald, FontWeight.Bold),
    Font(R.font.oswald, FontWeight.ExtraBold),
    Font(R.font.oswald, FontWeight.Black),
)

private fun neon(style: TextStyle): TextStyle = style.copy(
    fontFamily = OswaldText,
    letterSpacing = 0.3.sp
)

val OswaldTypography: Typography by lazy {
    val base = Typography()
    Typography(
        displayLarge = neon(base.displayLarge),
        displayMedium = neon(base.displayMedium),
        displaySmall = neon(base.displaySmall),
        headlineLarge = neon(base.headlineLarge),
        headlineMedium = neon(base.headlineMedium),
        headlineSmall = neon(base.headlineSmall),
        titleLarge = neon(base.titleLarge),
        titleMedium = neon(base.titleMedium),
        titleSmall = neon(base.titleSmall),
        bodyLarge = neon(base.bodyLarge),
        bodyMedium = neon(base.bodyMedium),
        bodySmall = neon(base.bodySmall),
        labelLarge = neon(base.labelLarge),
        labelMedium = neon(base.labelMedium),
        labelSmall = neon(base.labelSmall),
    )
}
