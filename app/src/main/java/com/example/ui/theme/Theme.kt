package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = Teal80,
    onPrimary = Teal10,
    primaryContainer = Teal30,
    onPrimaryContainer = Teal90,
    secondary = Amber80,
    onSecondary = Amber10,
    secondaryContainer = Amber30,
    onSecondaryContainer = Amber90,
    tertiary = NeutralVariant80,
    onTertiary = Neutral10,
    tertiaryContainer = NeutralVariant30,
    onTertiaryContainer = NeutralVariant90,
    error = ErrorRed80,
    onError = ErrorRed10,
    errorContainer = Color(0xFF93000A),
    onErrorContainer = ErrorRed90,
    background = Neutral10,
    onBackground = Neutral90,
    surface = Neutral10,
    onSurface = Neutral90,
    surfaceVariant = NeutralVariant30,
    onSurfaceVariant = NeutralVariant80,
    outline = NeutralVariant50,
  )

private val LightColorScheme =
  lightColorScheme(
    primary = Teal40,
    onPrimary = Neutral99,
    primaryContainer = Teal90,
    onPrimaryContainer = Teal20,
    secondary = Amber40,
    onSecondary = Neutral99,
    secondaryContainer = Amber90,
    onSecondaryContainer = Amber20,
    tertiary = NeutralVariant50,
    onTertiary = Neutral99,
    tertiaryContainer = NeutralVariant90,
    onTertiaryContainer = NeutralVariant30,
    error = ErrorRed40,
    onError = Neutral99,
    errorContainer = ErrorRed90,
    onErrorContainer = ErrorRed10,
    background = Neutral99,
    onBackground = Neutral10,
    surface = Neutral99,
    onSurface = Neutral10,
    surfaceVariant = NeutralVariant90,
    onSurfaceVariant = NeutralVariant30,
    outline = NeutralVariant50,
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is available on Android 12+
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
