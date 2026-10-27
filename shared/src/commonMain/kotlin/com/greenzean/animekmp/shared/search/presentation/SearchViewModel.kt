package com.greenzean.animekmp.shared.search.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.library.domain.AddToLibraryUseCase
import com.greenzean.animekmp.shared.library.domain.ObserveLibraryUseCase
import com.greenzean.animekmp.shared.mal.domain.MalAnimeSummary
import com.greenzean.animekmp.shared.mal.domain.MalError
import com.greenzean.animekmp.shared.mal.domain.toAnime
import com.greenzean.animekmp.shared.search.domain.SearchAnimeUseCase
import com.greenzean.animekmp.shared.settings.domain.ObserveTitleLanguageUseCase
import com.greenzean.animekmp.shared.settings.domain.pick
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val MIN_QUERY_LENGTH = 3
private const val DEBOUNCE_MS = 400L

private data class SearchProgress(
    val query: String = "",
    val found: List<MalAnimeSummary> = emptyList(),
    val status: SearchStatus = SearchStatus.Idle,
    val hasMore: Boolean = false,
)

class SearchViewModel(
    private val searchAnime: SearchAnimeUseCase,
    observeLibrary: ObserveLibraryUseCase,
    private val addToLibrary: AddToLibraryUseCase,
    observeTitleLanguage: ObserveTitleLanguageUseCase,
) : ViewModel() {
    private val progress = MutableStateFlow(SearchProgress())
    private val libraryIds = observeLibrary().map { entries -> entries.mapTo(mutableSetOf()) { it.anime.malId } }
    private val _events = Channel<SearchEvent>()
    val events = _events.receiveAsFlow()
    private var searchJob: Job? = null
    private var loadMoreJob: Job? = null

    val state: StateFlow<SearchState> =
        combine(progress, libraryIds, observeTitleLanguage()) { current, ids, language ->
            SearchState(
                query = current.query,
                results =
                    current.found.map {
                        SearchResultUi(it.malId, language.pick(it.title, it.titleEn), it.imageUrl, it.malId in ids)
                    },
                status = current.status,
                canLoadMore = current.status == SearchStatus.Results && current.hasMore,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, SearchState())

    fun onAction(action: SearchAction) {
        when (action) {
            is SearchAction.OnQueryChange -> {
                onQueryChange(action.text)
            }

            is SearchAction.OnAddClick -> {
                onAddClick(action.malId)
            }

            is SearchAction.OnResultClick -> {
                viewModelScope.launch {
                    _events.send(
                        SearchEvent.NavigateToDetails(action.malId),
                    )
                }
            }

            SearchAction.OnRetryClick -> {
                startSearch(delayMs = 0)
            }

            SearchAction.OnLoadMore -> {
                loadMore()
            }
        }
    }

    private fun onQueryChange(text: String) {
        progress.update { it.copy(query = text) }
        startSearch(delayMs = DEBOUNCE_MS)
    }

    private fun startSearch(delayMs: Long) {
        searchJob?.cancel()
        loadMoreJob?.cancel()
        val query = progress.value.query.trim()
        if (query.length < MIN_QUERY_LENGTH) {
            progress.update { SearchProgress(query = it.query) }
            return
        }
        searchJob =
            viewModelScope.launch {
                delay(delayMs)
                progress.update { it.copy(found = emptyList(), status = SearchStatus.Loading, hasMore = false) }
                when (val result = searchAnime(query)) {
                    is Result.Success -> {
                        val page = result.data
                        progress.update {
                            it.copy(
                                found = page.items,
                                status = if (page.items.isEmpty()) SearchStatus.Empty else SearchStatus.Results,
                                hasMore = page.hasMore,
                            )
                        }
                    }

                    is Result.Error -> {
                        val status =
                            if (result.error ==
                                MalError.NoConnection
                            ) {
                                SearchStatus.Offline
                            } else {
                                SearchStatus.Error
                            }
                        progress.update { it.copy(status = status) }
                    }
                }
            }
    }

    private fun loadMore() {
        val current = progress.value
        if (current.status != SearchStatus.Results || !current.hasMore || loadMoreJob?.isActive == true) return
        loadMoreJob =
            viewModelScope.launch {
                val result = searchAnime(current.query.trim(), pageStart = current.found.size)
                if (result is Result.Success) {
                    progress.update {
                        val known = it.found.mapTo(mutableSetOf()) { item -> item.malId }
                        it.copy(
                            found = it.found + result.data.items.filter { item -> item.malId !in known },
                            hasMore = result.data.hasMore,
                        )
                    }
                }
            }
    }

    private fun onAddClick(malId: Int) {
        if (state.value.results
                .firstOrNull { it.malId == malId }
                ?.inLibrary != false
        ) {
            return
        }
        val summary = progress.value.found.firstOrNull { it.malId == malId } ?: return
        viewModelScope.launch { addToLibrary(summary.toAnime()) }
    }
}
