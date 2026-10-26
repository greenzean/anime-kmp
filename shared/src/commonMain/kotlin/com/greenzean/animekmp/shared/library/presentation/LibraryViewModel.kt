package com.greenzean.animekmp.shared.library.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenzean.animekmp.shared.library.domain.ObserveLibraryUseCase
import com.greenzean.animekmp.shared.settings.domain.DisplaySettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class LibraryViewModel(
    observeLibrary: ObserveLibraryUseCase,
    displaySettings: DisplaySettings,
) : ViewModel() {
    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    init {
        combine(observeLibrary(), displaySettings.titleLanguage) { entries, language ->
            LibraryState(entries.map { it.toEntryUi(language) })
        }.onEach { _state.value = it }
            .launchIn(viewModelScope)
    }
}
