package com.greenzean.animekmp.shared.library.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenzean.animekmp.shared.library.domain.EditEntryUseCase
import com.greenzean.animekmp.shared.library.domain.EntryEdit
import com.greenzean.animekmp.shared.library.domain.LibrarySort
import com.greenzean.animekmp.shared.library.domain.ObserveLibraryIsEmptyUseCase
import com.greenzean.animekmp.shared.library.domain.ObserveLibrarySortUseCase
import com.greenzean.animekmp.shared.library.domain.ObserveLibraryUseCase
import com.greenzean.animekmp.shared.library.domain.SetLibrarySortUseCase
import com.greenzean.animekmp.shared.library.domain.WatchStatus
import com.greenzean.animekmp.shared.settings.domain.ObserveTitleLanguageUseCase
import com.greenzean.animekmp.shared.settings.domain.TitleLanguage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(
    private val observeLibrary: ObserveLibraryUseCase,
    observeLibraryIsEmpty: ObserveLibraryIsEmptyUseCase,
    observeLibrarySort: ObserveLibrarySortUseCase,
    private val setLibrarySort: SetLibrarySortUseCase,
    observeTitleLanguage: ObserveTitleLanguageUseCase,
    private val editEntry: EditEntryUseCase,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val statusFilter = MutableStateFlow<WatchStatus?>(null)
    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state.asStateFlow()
    private val _events = Channel<LibraryEvent>()
    val events = _events.receiveAsFlow()

    init {
        val entries =
            combine(query, statusFilter, observeLibrarySort(), observeTitleLanguage()) { q, status, sort, language ->
                LibraryRequest(q, status, sort, language)
            }.flatMapLatest { request ->
                observeLibrary(request.query.trim(), request.status, request.sort, request.language)
                    .map { list -> request to list.map { it.toEntryUi(request.language) } }
            }
        combine(entries, observeLibraryIsEmpty()) { (request, list), isEmpty ->
            LibraryState(
                entries = list,
                query = request.query,
                statusFilter = request.status,
                sort = request.sort,
                isLibraryEmpty = isEmpty,
            )
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

            is LibraryAction.OnQueryChange -> {
                query.value = action.query
            }

            is LibraryAction.OnStatusFilterClick -> {
                statusFilter.update { current -> if (action.status == current) null else action.status }
            }

            is LibraryAction.OnSortChange -> {
                setLibrarySort(action.sort)
            }

            LibraryAction.OnSettingsClick -> {
                viewModelScope.launch { _events.send(LibraryEvent.NavigateToSettings) }
            }
        }
    }
}

private data class LibraryRequest(
    val query: String,
    val status: WatchStatus?,
    val sort: LibrarySort,
    val language: TitleLanguage,
)
