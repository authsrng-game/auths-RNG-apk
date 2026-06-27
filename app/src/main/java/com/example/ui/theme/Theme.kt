package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = MinimalWhite,
    secondary = Zinc400,
    background = DarkBackground,
    surface = DarkSurface,
    onPrimary = DarkBackground,
    onSecondary = MinimalWhite,
    onBackground = MinimalWhite,
    onSurface = MinimalWhite,
    outline = Zinc800
  )

private val LightColorScheme = DarkColorScheme // Always dark minimalist theme

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Default to true (always dark)
  dynamicColor: Boolean = false, // Keep minimalist branding consistent
  content: @Composable () -> Unit,
) {
  val colorScheme = DarkColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
