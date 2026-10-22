package com.greenzean.animekmp.shared.library.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenzean.animekmp.shared.library.domain.AddToLibraryUseCase
import com.greenzean.animekmp.shared.library.domain.ObserveAddableAnimeUseCase
import com.greenzean.animekmp.shared.library.domain.ObserveLibraryUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LibraryViewModel(
    observeLibrary: ObserveLibraryUseCase,
    observeAddableAnime: ObserveAddableAnimeUseCase,
    private val addToLibrary: AddToLibraryUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    init {
        observeLibrary()
            .onEach { entries -> _state.update { it.copy(entries = entries.map { entry -> entry.toEntryUi() }) } }
            .launchIn(viewModelScope)
        observeAddableAnime()
            .onEach { anime -> _state.update { it.copy(addable = anime.map { a -> a.toAnimeUi() }) } }
            .launchIn(viewModelScope)
    }

    fun onAction(action: LibraryAction) {
        when (action) {
            is LibraryAction.OnAddClick -> viewModelScope.launch { addToLibrary(action.malId) }
        }
    }
}
