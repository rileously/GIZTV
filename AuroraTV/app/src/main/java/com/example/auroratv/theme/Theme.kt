package com.example.auroratv.theme

import androidx.compose.runtime.Composable
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

private val AuroraColorScheme =
  darkColorScheme(
    primary = AuroraBlue,
    secondary = AuroraMint,
    background = DeepSpace,
    surface = NightSurface,
    onPrimary = DeepSpace,
    onSecondary = DeepSpace,
    onBackground = SoftWhite,
    onSurface = SoftWhite,
  )

@Composable
fun AuroraTVTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = AuroraColorScheme,
    typography = Typography,
    content = content,
  )
}
