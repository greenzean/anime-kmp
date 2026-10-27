package com.greenzean.animekmp.shared.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenzean.animekmp.shared.settings.domain.ObserveTitleLanguageUseCase
import com.greenzean.animekmp.shared.settings.domain.SetTitleLanguageUseCase
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
    observeTitleLanguage: ObserveTitleLanguageUseCase,
    private val setTitleLanguage: SetTitleLanguageUseCase,
) : ViewModel() {
    val state: StateFlow<DisplaySettingsState> =
        observeTitleLanguage()
            .map { DisplaySettingsState(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, DisplaySettingsState(observeTitleLanguage().value))

    fun onAction(action: DisplaySettingsAction) {
        when (action) {
            is DisplaySettingsAction.OnTitleLanguageChange -> setTitleLanguage(action.language)
        }
    }
}
