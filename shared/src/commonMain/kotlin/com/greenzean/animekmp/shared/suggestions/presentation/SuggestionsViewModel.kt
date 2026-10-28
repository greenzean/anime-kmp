package com.greenzean.animekmp.shared.suggestions.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.library.domain.AddToLibraryUseCase
import com.greenzean.animekmp.shared.library.domain.ObserveLibraryUseCase
import com.greenzean.animekmp.shared.mal.domain.MalAnimeSummary
import com.greenzean.animekmp.shared.mal.domain.MalError
import com.greenzean.animekmp.shared.mal.domain.ObserveMalAccountUseCase
import com.greenzean.animekmp.shared.mal.domain.toAnime
import com.greenzean.animekmp.shared.search.presentation.SearchResultUi
import com.greenzean.animekmp.shared.settings.domain.ObserveTitleLanguageUseCase
import com.greenzean.animekmp.shared.settings.domain.pick
import com.greenzean.animekmp.shared.suggestions.domain.GetSuggestionsUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private data class SuggestionsProgress(
    val found: List<MalAnimeSummary> = emptyList(),
    val fetchedCount: Int = 0,
    val status: SuggestionsStatus = SuggestionsStatus.Loading,
    val hasMore: Boolean = false,
)

class SuggestionsViewModel(
    observeMalAccount: ObserveMalAccountUseCase,
    private val getSuggestions: GetSuggestionsUseCase,
    observeLibrary: ObserveLibraryUseCase,
    private val addToLibrary: AddToLibraryUseCase,
    observeTitleLanguage: ObserveTitleLanguageUseCase,
) : ViewModel() {
    private val progress = MutableStateFlow(SuggestionsProgress())
    private val libraryIds = observeLibrary().map { entries -> entries.mapTo(mutableSetOf()) { it.anime.malId } }
    private val _events = Channel<SuggestionsEvent>()
    private var retryJob: Job? = null
    private var loadMoreJob: Job? = null

    val events: Flow<SuggestionsEvent> = _events.receiveAsFlow()

    val state: StateFlow<SuggestionsState> =
        combine(progress, libraryIds, observeTitleLanguage()) { current, ids, language ->
            SuggestionsState(
                items =
                    current.found
                        .filter { it.malId !in ids }
                        .map { SearchResultUi(it.malId, language.pick(it.title, it.titleEn), it.imageUrl, false) },
                status = current.status,
                canLoadMore = current.status == SuggestionsStatus.Loaded && current.hasMore,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, SuggestionsState())

    init {
        viewModelScope.launch {
            observeMalAccount()
                .map { it.connected }
                .distinctUntilChanged()
                .collectLatest { connected ->
                    retryJob?.cancel()
                    loadMoreJob?.cancel()
                    if (connected) {
                        loadFirstPage()
                    } else {
                        progress.value = SuggestionsProgress(status = SuggestionsStatus.NotConnected)
                    }
                }
        }
    }

    fun onAction(action: SuggestionsAction) {
        when (action) {
            is SuggestionsAction.OnAddClick -> {
                onAddClick(action.malId)
            }

            is SuggestionsAction.OnItemClick -> {
                viewModelScope.launch { _events.send(SuggestionsEvent.NavigateToDetails(action.malId)) }
            }

            SuggestionsAction.OnRetryClick -> {
                retry()
            }

            SuggestionsAction.OnLoadMore -> {
                loadMore()
            }
        }
    }

    private fun retry() {
        if (progress.value.status == SuggestionsStatus.NotConnected) return
        retryJob?.cancel()
        loadMoreJob?.cancel()
        retryJob = viewModelScope.launch { loadFirstPage() }
    }

    private suspend fun loadFirstPage() {
        progress.value = SuggestionsProgress(status = SuggestionsStatus.Loading)
        when (val result = getSuggestions(0)) {
            is Result.Success -> {
                val page = result.data
                progress.value =
                    SuggestionsProgress(
                        found = page.items.distinctBy { it.malId },
                        fetchedCount = page.items.size,
                        status = if (page.items.isEmpty()) SuggestionsStatus.Empty else SuggestionsStatus.Loaded,
                        hasMore = page.hasMore,
                    )
            }

            is Result.Error -> {
                val status =
                    if (result.error == MalError.NoConnection) SuggestionsStatus.Offline else SuggestionsStatus.Error
                progress.value = SuggestionsProgress(status = status)
            }
        }
    }

    private fun loadMore() {
        val current = progress.value
        if (current.status != SuggestionsStatus.Loaded || !current.hasMore) return
        if (loadMoreJob?.isActive == true || retryJob?.isActive == true) return
        loadMoreJob =
            viewModelScope.launch {
                val result = getSuggestions(current.fetchedCount)
                if (result is Result.Success) {
                    progress.update {
                        val known = it.found.mapTo(mutableSetOf()) { item -> item.malId }
                        it.copy(
                            found = it.found + result.data.items.filter { item -> known.add(item.malId) },
                            fetchedCount = it.fetchedCount + result.data.items.size,
                            hasMore = result.data.hasMore,
                        )
                    }
                }
            }
    }

    private fun onAddClick(malId: Int) {
        if (state.value.items.none { it.malId == malId }) return
        val summary = progress.value.found.firstOrNull { it.malId == malId } ?: return
        viewModelScope.launch { addToLibrary(summary.toAnime()) }
    }
}
