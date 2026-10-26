package com.greenzean.animekmp.shared.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenzean.animekmp.shared.settings.domain.DisplaySettings
import com.greenzean.animekmp.shared.settings.domain.TitleLanguage
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class DisplaySettingsState(
    val titleLanguage: TitleLanguage = TitleLanguage.ENGLISH,
)

sealed interface DisplaySettingsAction {
    data class OnTitleLanguageChange(
        val language: TitleLanguage,
    ) : DisplaySettingsAction
}

class DisplaySettingsViewModel(
    private val settings: DisplaySettings,
) : ViewModel() {
    val state: StateFlow<DisplaySettingsState> =
        settings.titleLanguage
            .map { DisplaySettingsState(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, DisplaySettingsState(settings.titleLanguage.value))

    fun onAction(action: DisplaySettingsAction) {
        when (action) {
            is DisplaySettingsAction.OnTitleLanguageChange -> settings.setTitleLanguage(action.language)
        }
    }
}
