package com.greenzean.animekmp.shared.library.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenzean.animekmp.shared.library.domain.EditEntryUseCase
import com.greenzean.animekmp.shared.library.domain.EntryEdit
import com.greenzean.animekmp.shared.library.domain.ObserveLibraryUseCase
import com.greenzean.animekmp.shared.settings.domain.ObserveTitleLanguageUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LibraryViewModel(
    observeLibrary: ObserveLibraryUseCase,
    observeTitleLanguage: ObserveTitleLanguageUseCase,
    private val editEntry: EditEntryUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state.asStateFlow()
    private val _events = Channel<LibraryEvent>()
    val events = _events.receiveAsFlow()

    init {
        combine(observeLibrary(), observeTitleLanguage()) { entries, language ->
            LibraryState(entries.map { it.toEntryUi(language) })
        }.onEach { next -> _state.update { next } }
            .launchIn(viewModelScope)
    }

    fun onAction(action: LibraryAction) {
        when (action) {
            is LibraryAction.OnEntryClick -> {
                viewModelScope.launch {
                    _events.send(
                        LibraryEvent.NavigateToDetails(action.malId),
                    )
                }
            }

            is LibraryAction.OnIncrementClick -> {
                viewModelScope.launch { editEntry(action.malId, EntryEdit.Increment) }
            }

            LibraryAction.OnSettingsClick -> {
                viewModelScope.launch { _events.send(LibraryEvent.NavigateToSettings) }
            }
        }
    }
}
