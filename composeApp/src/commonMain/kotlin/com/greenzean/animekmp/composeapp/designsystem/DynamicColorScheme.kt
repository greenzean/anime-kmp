package com.greenzean.animekmp.composeapp.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

/** Wallpaper-based colors where the platform supports them (Android 12+), otherwise null. */
@Composable
expect fun dynamicColorScheme(darkTheme: Boolean): ColorScheme?
