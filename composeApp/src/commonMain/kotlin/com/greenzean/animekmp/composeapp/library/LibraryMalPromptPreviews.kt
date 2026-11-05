package com.greenzean.animekmp.composeapp.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.greenzean.animekmp.composeapp.designsystem.ThemedPreview

@Preview
@Composable
private fun LibraryMalPromptLightPreview() = LibraryMalPromptPreview(darkTheme = false)

@Preview
@Composable
private fun LibraryMalPromptDarkPreview() = LibraryMalPromptPreview(darkTheme = true)

@Composable
private fun LibraryMalPromptPreview(darkTheme: Boolean) {
    ThemedPreview(darkTheme) {
        LibraryScreen(state = previewFiltered.copy(showMalPrompt = true), onAction = {})
    }
}
