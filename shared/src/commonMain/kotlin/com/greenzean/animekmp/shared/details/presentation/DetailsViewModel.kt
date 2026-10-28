package com.greenzean.animekmp.shared.details.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.details.domain.GetAnimeDetailsUseCase
import com.greenzean.animekmp.shared.library.domain.AddToLibraryUseCase
import com.greenzean.animekmp.shared.library.domain.EditEntryUseCase
import com.greenzean.animekmp.shared.library.domain.EntryEdit
import com.greenzean.animekmp.shared.library.domain.LibraryEntry
import com.greenzean.animekmp.shared.library.domain.ObserveLibraryUseCase
import com.greenzean.animekmp.shared.library.domain.RemoveFromLibraryUseCase
import com.greenzean.animekmp.shared.library.domain.RestoreEntryUseCase
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
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant

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
    private val editEntry: EditEntryUseCase,
    private val removeFromLibrary: RemoveFromLibraryUseCase,
    private val restoreEntry: RestoreEntryUseCase,
    private val clock: Clock,
) : ViewModel() {
    private val progress = MutableStateFlow(DetailsProgress())
    private val libraryEntry =
        observeLibrary()
            .map { entries -> entries.firstOrNull { it.anime.malId == malId } }
            .onEach { latestEntry = it }
    private var latestEntry: LibraryEntry? = null
    private var removed: LibraryEntry? = null
    private var loadJob: Job? = null
    private var addJob: Job? = null
    private val _events = Channel<DetailsEvent>()
    val events = _events.receiveAsFlow()

    val state: StateFlow<DetailsState> =
        combine(progress, libraryEntry, observeTitleLanguage()) { current, entry, language ->
            val details = current.found?.toDetailsUi(language)
            DetailsState(
                details = details,
                entry = entry?.toEntryEditUi(details, clock.now()),
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
            is DetailsAction.OnStatusChange -> edit(EntryEdit.Status(action.status))
            is DetailsAction.OnProgressChange -> edit(EntryEdit.Progress(action.progress))
            is DetailsAction.OnScoreChange -> edit(EntryEdit.Score(action.score))
            DetailsAction.OnRemoveClick -> onRemoveClick()
            DetailsAction.OnUndoRemoveClick -> onUndoRemoveClick()
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
                            when (result.error) {
                                MalError.NoConnection -> DetailsStatus.Offline
                                MalError.NotFound -> DetailsStatus.NotFound
                                is MalError.Throttled -> DetailsStatus.Throttled
                                else -> DetailsStatus.Error
                            }
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

    private fun edit(edit: EntryEdit) {
        val totalEpisodes = state.value.details?.episodes
        viewModelScope.launch { editEntry(malId, edit, totalEpisodes) }
    }

    private fun onRemoveClick() {
        val current = latestEntry ?: return
        viewModelScope.launch {
            removed = current
            removeFromLibrary(malId)
            _events.send(DetailsEvent.ShowRemoved)
        }
    }

    private fun onUndoRemoveClick() {
        val snapshot = removed ?: return
        removed = null
        viewModelScope.launch { restoreEntry(snapshot) }
    }
}

private fun LibraryEntry.toEntryEditUi(
    details: AnimeDetailsUi?,
    now: Instant,
) = EntryEditUi(
    status = status,
    progress = progress,
    totalEpisodes = details?.episodes ?: anime.episodes,
    score = score,
    selectableEpisodes = selectableEpisodes(details, anime.episodes, progress, now),
)
