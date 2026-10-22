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
    private val bebop = Anime(malId = 1, title = "Cowboy Bebop")
    private val steins = Anime(malId = 9253, title = "Steins;Gate")
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
        assertEquals(listOf(AnimeUi(1, "Cowboy Bebop"), AnimeUi(9253, "Steins;Gate")), state.addable)
    }

    @Test
    fun addMovesAnimeToEntriesAsPendingAndRequestsSync() {
        val viewModel = viewModel()

        viewModel.onAction(LibraryAction.OnAddClick(malId = 9253))

        val state = viewModel.state.value
        val expected = EntryUi("entry-9253", "Steins;Gate", WatchStatus.PLAN_TO_WATCH, SyncState.PENDING)
        assertEquals(listOf(expected), state.entries)
        assertEquals(listOf(AnimeUi(1, "Cowboy Bebop")), state.addable)
        assertEquals(1, scheduler.requests)
    }

    @Test
    fun showsSyncedEntryFromLocalDatabase() {
        val viewModel = viewModel()
        viewModel.onAction(LibraryAction.OnAddClick(malId = 1))

        local.markSynced(malId = 1)

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
        malId: Int,
        status: WatchStatus,
    ) {
        val target = anime.value.first { it.malId == malId }
        library.update { entries ->
            entries.filterNot { it.anime.malId == malId } +
                LibraryEntry("entry-$malId", target, status, Instant.fromEpochMilliseconds(0), SyncState.PENDING)
        }
    }

    override suspend fun remove(malId: Int) {
        library.update { entries -> entries.filterNot { it.anime.malId == malId } }
    }

    fun markSynced(malId: Int) {
        library.update { entries ->
            entries.map { if (it.anime.malId == malId) it.copy(syncState = SyncState.SYNCED) else it }
        }
    }
}
