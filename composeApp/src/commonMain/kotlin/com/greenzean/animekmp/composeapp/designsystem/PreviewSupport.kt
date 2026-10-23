package com.greenzean.animekmp.composeapp.designsystem

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable

/** Renders [content] in the app theme, light and dark, for previews. */
@Composable
internal fun ThemedPreview(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    AppTheme(darkTheme = darkTheme) { Surface(content = content) }
}
