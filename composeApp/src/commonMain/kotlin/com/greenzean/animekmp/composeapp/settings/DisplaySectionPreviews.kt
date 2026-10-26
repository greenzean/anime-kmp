package com.greenzean.animekmp.composeapp.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.greenzean.animekmp.composeapp.designsystem.ThemedPreview
import com.greenzean.animekmp.shared.settings.domain.TitleLanguage
import com.greenzean.animekmp.shared.settings.presentation.DisplaySettingsState

@Preview
@Composable
private fun DisplaySectionLightPreview() = DisplaySectionPreview(darkTheme = false)

@Preview
@Composable
private fun DisplaySectionDarkPreview() = DisplaySectionPreview(darkTheme = true)

@Composable
private fun DisplaySectionPreview(darkTheme: Boolean) {
    ThemedPreview(darkTheme) {
        DisplaySection(
            state = DisplaySettingsState(TitleLanguage.MAIN),
            onAction = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
