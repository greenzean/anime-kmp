package com.greenzean.animekmp.shared.library.presentation

import com.greenzean.animekmp.shared.library.domain.AddToLibraryUseCase
import com.greenzean.animekmp.shared.library.domain.Anime
import com.greenzean.animekmp.shared.library.domain.LibraryEntry
import com.greenzean.animekmp.shared.library.domain.LibraryLocalDataSource
import com.greenzean.animekmp.shared.library.domain.ObserveAddableAnimeUseCase
import com.greenzean.animekmp.shared.library.domain.ObserveLibraryUseCase
import com.greenzean.animekmp.shared.library.domain.SyncState
import com.greenzean.animekmp.shared.library.domain.WatchStatus
import com.greenzean.animekmp.shared.sync.domain.SyncScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {
    private val bebop = Anime(id = 1, malId = 1, title = "Cowboy Bebop")
    private val steins = Anime(id = 3, malId = 9253, title = "Steins;Gate")
    private val local = FakeLibraryLocalDataSource(listOf(bebop, steins))
    private val scheduler = FakeSyncScheduler()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() =
        LibraryViewModel(
            ObserveLibraryUseCase(local),
            ObserveAddableAnimeUseCase(local),
            AddToLibraryUseCase(local, scheduler),
        )

    @Test
    fun showsAllAnimeAsAddableWhenLibraryIsEmpty() {
        val state = viewModel().state.value

        assertEquals(emptyList(), state.entries)
        assertEquals(listOf(AnimeUi(1, "Cowboy Bebop"), AnimeUi(3, "Steins;Gate")), state.addable)
    }

    @Test
    fun addMovesAnimeToEntriesAsPendingAndRequestsSync() {
        val viewModel = viewModel()

        viewModel.onAction(LibraryAction.OnAddClick(animeId = 3))

        val state = viewModel.state.value
        val expected = EntryUi("entry-3", "Steins;Gate", WatchStatus.PLAN_TO_WATCH, SyncState.PENDING)
        assertEquals(listOf(expected), state.entries)
        assertEquals(listOf(AnimeUi(1, "Cowboy Bebop")), state.addable)
        assertEquals(1, scheduler.requests)
    }

    @Test
    fun showsSyncedEntryFromLocalDatabase() {
        val viewModel = viewModel()
        viewModel.onAction(LibraryAction.OnAddClick(animeId = 1))

        local.markSynced(animeId = 1)

        assertEquals(
            SyncState.SYNCED,
            viewModel.state.value.entries
                .single()
                .syncState,
        )
    }
}

private class FakeSyncScheduler : SyncScheduler {
    var requests = 0

    override fun requestSync() {
        requests++
    }
}

private class FakeLibraryLocalDataSource(
    anime: List<Anime>,
) : LibraryLocalDataSource {
    private val anime = MutableStateFlow(anime)
    private val library = MutableStateFlow<List<LibraryEntry>>(emptyList())

    override fun observeAnime(): Flow<List<Anime>> = anime

    override fun observeLibrary(): Flow<List<LibraryEntry>> = library

    override suspend fun setStatus(
        animeId: Long,
        status: WatchStatus,
    ) {
        val target = anime.value.first { it.id == animeId }
        library.update { entries ->
            entries.filterNot { it.anime.id == animeId } +
                LibraryEntry("entry-$animeId", target, status, Instant.fromEpochMilliseconds(0), SyncState.PENDING)
        }
    }

    override suspend fun remove(animeId: Long) {
        library.update { entries -> entries.filterNot { it.anime.id == animeId } }
    }

    fun markSynced(animeId: Long) {
        library.update { entries ->
            entries.map { if (it.anime.id == animeId) it.copy(syncState = SyncState.SYNCED) else it }
        }
    }
}
