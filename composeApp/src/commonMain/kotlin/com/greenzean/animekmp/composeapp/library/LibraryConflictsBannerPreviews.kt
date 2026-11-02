package com.greenzean.animekmp.composeapp.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.greenzean.animekmp.composeapp.designsystem.ThemedPreview

@Preview
@Composable
private fun LibraryBannerLightPreview() = LibraryBannerPreview(darkTheme = false)

@Preview
@Composable
private fun LibraryBannerDarkPreview() = LibraryBannerPreview(darkTheme = true)

@Composable
private fun LibraryBannerPreview(darkTheme: Boolean) {
    ThemedPreview(darkTheme) {
        LibraryScreen(state = previewFiltered.copy(conflictCount = 3), onAction = {})
    }
}
