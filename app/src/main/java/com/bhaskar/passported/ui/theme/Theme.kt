package com.bhaskar.passported.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Use a default color scheme if isSystemColorAvailable is not supported
private val DarkColorScheme = darkColorScheme(
    primary = Teal700Dark,
    secondary = Teal200Dark,
    tertiary = LightGreen500Dark,
    surface = Color(0xFF121212), // Darker surface color
    background = Color(0xFF0F0F0F), // Darker background color
    onSurface = Color.White, // Text color on dark surfaces
    onBackground = Color.White, // Text color on dark background
)

private val LightColorScheme = lightColorScheme(
    primary = Teal700,
    secondary = Teal200,
    tertiary = LightGreen500,
    surface = Color(0xFFF2F2F2), // Lighter surface color
    background = Color(0xFFF5F5F5), // Lighter background color
    onSurface = Color.Black, // Text color on light surfaces
    onBackground = Color.Black, // Text color on light background
)

@Composable
fun PassportedTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}