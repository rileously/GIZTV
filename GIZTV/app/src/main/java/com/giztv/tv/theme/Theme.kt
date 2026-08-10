package com.giztv.tv.theme

import androidx.compose.runtime.Composable
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

private val GizColorScheme =
  darkColorScheme(
    primary = GizBlue,
    secondary = GizMint,
    background = DeepSpace,
    surface = NightSurface,
    onPrimary = DeepSpace,
    onSecondary = DeepSpace,
    onBackground = SoftWhite,
    onSurface = SoftWhite,
  )

@Composable
fun GizTvTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = GizColorScheme,
    typography = Typography,
    content = content,
  )
}
