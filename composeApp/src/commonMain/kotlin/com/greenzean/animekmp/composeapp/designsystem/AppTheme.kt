package com.greenzean.animekmp.composeapp.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors =
    lightColorScheme(
        primary = Color(0xFF3F51B5),
        onPrimary = Color.White,
        secondary = Color(0xFF5C6BC0),
    )

private val DarkColors =
    darkColorScheme(
        primary = Color(0xFFB6BEFF),
        onPrimary = Color(0xFF14206B),
        secondary = Color(0xFFC0C6F5),
    )

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme: ColorScheme = dynamicColorScheme(darkTheme) ?: if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colorScheme, content = content)
}
