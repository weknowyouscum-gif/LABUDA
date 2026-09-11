package com.labuda.app

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.res.fontResource

val Tele2TextSans = FontFamily(
    Font(R.font.tele2_text_sans, FontWeight.Normal),
)

private fun tele2(style: TextStyle): TextStyle = style.copy(fontFamily = Tele2TextSans)

val Tele2Typography: Typography by lazy {
    val base = Typography()
    Typography(
        displayLarge = tele2(base.displayLarge),
        displayMedium = tele2(base.displayMedium),
        displaySmall = tele2(base.displaySmall),
        headlineLarge = tele2(base.headlineLarge),
        headlineMedium = tele2(base.headlineMedium),
        headlineSmall = tele2(base.headlineSmall),
        titleLarge = tele2(base.titleLarge),
        titleMedium = tele2(base.titleMedium),
        titleSmall = tele2(base.titleSmall),
        bodyLarge = tele2(base.bodyLarge),
        bodyMedium = tele2(base.bodyMedium),
        bodySmall = tele2(base.bodySmall),
        labelLarge = tele2(base.labelLarge),
        labelMedium = tele2(base.labelMedium),
        labelSmall = tele2(base.labelSmall),
    )
}
