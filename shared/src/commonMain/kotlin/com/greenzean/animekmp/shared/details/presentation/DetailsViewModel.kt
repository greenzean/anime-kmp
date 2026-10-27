package com.greenzean.animekmp.shared.details.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.details.domain.GetAnimeDetailsUseCase
import com.greenzean.animekmp.shared.library.domain.AddToLibraryUseCase
import com.greenzean.animekmp.shared.library.domain.ObserveLibraryUseCase
import com.greenzean.animekmp.shared.mal.domain.MalAnimeDetails
import com.greenzean.animekmp.shared.mal.domain.MalError
import com.greenzean.animekmp.shared.mal.domain.toAnime
import com.greenzean.animekmp.shared.settings.domain.ObserveTitleLanguageUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private data class DetailsProgress(
    val found: MalAnimeDetails? = null,
    val status: DetailsStatus = DetailsStatus.Loading,
)

class DetailsViewModel(
    private val malId: Int,
    private val getAnimeDetails: GetAnimeDetailsUseCase,
    observeLibrary: ObserveLibraryUseCase,
    private val addToLibrary: AddToLibraryUseCase,
    observeTitleLanguage: ObserveTitleLanguageUseCase,
) : ViewModel() {
    private val progress = MutableStateFlow(DetailsProgress())
    private val libraryIds = observeLibrary().map { entries -> entries.mapTo(mutableSetOf()) { it.anime.malId } }
    private var loadJob: Job? = null
    private var addJob: Job? = null
    private val _events = Channel<DetailsEvent>()
    val events = _events.receiveAsFlow()

    val state: StateFlow<DetailsState> =
        combine(progress, libraryIds, observeTitleLanguage()) { current, ids, language ->
            DetailsState(
                details = current.found?.toDetailsUi(language),
                inLibrary = malId in ids,
                status = current.status,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, DetailsState())

    init {
        load()
    }

    fun onAction(action: DetailsAction) {
        when (action) {
            DetailsAction.OnAddClick -> onAddClick()
            DetailsAction.OnRetryClick -> load()
            DetailsAction.OnBackClick -> viewModelScope.launch { _events.send(DetailsEvent.NavigateBack) }
        }
    }

    private fun load() {
        loadJob?.cancel()
        loadJob =
            viewModelScope.launch {
                progress.update { DetailsProgress(status = DetailsStatus.Loading) }
                when (val result = getAnimeDetails(malId)) {
                    is Result.Success -> {
                        progress.update { DetailsProgress(found = result.data, status = DetailsStatus.Loaded) }
                    }

                    is Result.Error -> {
                        val status =
                            if (result.error == MalError.NoConnection) DetailsStatus.Offline else DetailsStatus.Error
                        progress.update { DetailsProgress(status = status) }
                    }
                }
            }
    }

    private fun onAddClick() {
        val found = progress.value.found ?: return
        if (state.value.inLibrary || addJob?.isActive == true) return
        addJob = viewModelScope.launch { addToLibrary(found.summary.toAnime()) }
    }
}
