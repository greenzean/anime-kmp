package com.greenzean.animekmp.composeapp.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.greenzean.animekmp.composeapp.designsystem.components.AppCard
import com.greenzean.animekmp.composeapp.designsystem.components.AppRadioRow
import com.greenzean.animekmp.composeapp.designsystem.spacing
import com.greenzean.animekmp.composeapp.generated.resources.Res
import com.greenzean.animekmp.composeapp.generated.resources.display_english
import com.greenzean.animekmp.composeapp.generated.resources.display_english_supporting
import com.greenzean.animekmp.composeapp.generated.resources.display_heading
import com.greenzean.animekmp.composeapp.generated.resources.display_main_title
import com.greenzean.animekmp.composeapp.generated.resources.display_title_language
import com.greenzean.animekmp.shared.settings.domain.TitleLanguage
import com.greenzean.animekmp.shared.settings.presentation.DisplaySettingsAction
import com.greenzean.animekmp.shared.settings.presentation.DisplaySettingsState
import org.jetbrains.compose.resources.stringResource

@Composable
fun DisplaySection(
    state: DisplaySettingsState,
    onAction: (DisplaySettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            stringResource(Res.string.display_heading),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = MaterialTheme.spacing.xs).semantics { heading() },
        )
        AppCard(modifier = Modifier.padding(top = MaterialTheme.spacing.md)) {
            Text(stringResource(Res.string.display_title_language), style = MaterialTheme.typography.titleSmall)
            Column(modifier = Modifier.selectableGroup()) {
                AppRadioRow(
                    label = stringResource(Res.string.display_english),
                    supportingText = stringResource(Res.string.display_english_supporting),
                    selected = state.titleLanguage == TitleLanguage.ENGLISH,
                    onSelect = { onAction(DisplaySettingsAction.OnTitleLanguageChange(TitleLanguage.ENGLISH)) },
                )
                AppRadioRow(
                    label = stringResource(Res.string.display_main_title),
                    selected = state.titleLanguage == TitleLanguage.MAIN,
                    onSelect = { onAction(DisplaySettingsAction.OnTitleLanguageChange(TitleLanguage.MAIN)) },
                )
            }
        }
    }
}
