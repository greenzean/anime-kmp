package com.greenzean.animekmp.shared.library.presentation

import app.cash.turbine.test
import com.greenzean.animekmp.shared.auth.FakeSyncScheduler
import com.greenzean.animekmp.shared.library.domain.Anime
import com.greenzean.animekmp.shared.library.domain.EditEntryUseCase
import com.greenzean.animekmp.shared.library.domain.LibraryEntry
import com.greenzean.animekmp.shared.library.domain.LibraryLocalDataSource
import com.greenzean.animekmp.shared.library.domain.ObserveLibraryUseCase
import com.greenzean.animekmp.shared.library.domain.SyncState
import com.greenzean.animekmp.shared.library.domain.WatchStatus
import com.greenzean.animekmp.shared.settings.FakeDisplaySettings
import com.greenzean.animekmp.shared.settings.domain.ObserveTitleLanguageUseCase
import com.greenzean.animekmp.shared.settings.domain.TitleLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {
    private val frieren = Anime(52991, "Sousou no Frieren", "Frieren: Beyond Journey's End", "https://img/52991.jpg")
    private val bebop = Anime(1, "Cowboy Bebop")
    private val local = FakeLibraryLocalDataSource()
    private val settings = FakeDisplaySettings()
    private val scheduler = FakeSyncScheduler()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() =
        LibraryViewModel(
            ObserveLibraryUseCase(local),
            ObserveTitleLanguageUseCase(settings),
            EditEntryUseCase(local, scheduler),
        )

    @Test
    fun entryClickEmitsNavigateToDetails() =
        runTest {
            val viewModel = viewModel()

            viewModel.events.test {
                viewModel.onAction(LibraryAction.OnEntryClick(52991))
                advanceUntilIdle()

                assertEquals(LibraryEvent.NavigateToDetails(52991), awaitItem())
            }
        }

    @Test
    fun settingsClickEmitsNavigateToSettings() =
        runTest {
            val viewModel = viewModel()

            viewModel.events.test {
                viewModel.onAction(LibraryAction.OnSettingsClick)
                advanceUntilIdle()

                assertEquals(LibraryEvent.NavigateToSettings, awaitItem())
            }
        }

    @Test
    fun startsEmpty() {
        assertEquals(emptyList(), viewModel().state.value.entries)
    }

    @Test
    fun showsEntriesWithTheEnglishTitleAndCover() {
        local.put(frieren, SyncState.PENDING)

        val entry =
            viewModel()
                .state.value.entries
                .single()

        assertEquals(
            EntryUi(
                "entry-52991",
                52991,
                "Frieren: Beyond Journey's End",
                "https://img/52991.jpg",
                WatchStatus.PLAN_TO_WATCH,
                0,
                null,
                null,
                SyncState.PENDING,
            ),
            entry,
        )
    }

    @Test
    fun fallsBackToTheMainTitleWhenThereIsNoEnglishTitle() {
        local.put(bebop, SyncState.SYNCED)

        val entry =
            viewModel()
                .state.value.entries
                .single()

        assertEquals("Cowboy Bebop", entry.title)
        assertEquals(null, entry.imageUrl)
    }

    @Test
    fun switchingTheTitleLanguageChangesTheTitles() {
        local.put(frieren, SyncState.SYNCED)
        val viewModel = viewModel()

        settings.setTitleLanguage(TitleLanguage.MAIN)

        assertEquals(
            "Sousou no Frieren",
            viewModel.state.value.entries
                .single()
                .title,
        )
    }

    @Test
    fun followsChangesInTheLibrary() {
        val viewModel = viewModel()

        local.put(bebop, SyncState.PENDING)
        local.put(bebop, SyncState.SYNCED)

        assertEquals(
            SyncState.SYNCED,
            viewModel.state.value.entries
                .single()
                .syncState,
        )
    }

    @Test
    fun showsProgressScoreAndTotalEpisodes() {
        local.put(frieren.copy(episodes = 28), SyncState.SYNCED, WatchStatus.WATCHING, progress = 5, score = 9)

        val entry =
            viewModel()
                .state.value.entries
                .single()

        assertEquals(5, entry.progress)
        assertEquals(28, entry.totalEpisodes)
        assertEquals(9, entry.score)
    }

    @Test
    fun incrementClickAddsOneEpisodeAndRequestsSync() =
        runTest {
            local.put(frieren.copy(episodes = 28), SyncState.SYNCED, WatchStatus.WATCHING, progress = 5)
            val viewModel = viewModel()

            viewModel.onAction(LibraryAction.OnIncrementClick(52991))
            advanceUntilIdle()

            assertEquals(SavedEntry(52991, WatchStatus.WATCHING, 6, null), local.savedEntries.single())
            assertEquals(
                6,
                viewModel.state.value.entries
                    .single()
                    .progress,
            )
            assertEquals(1, scheduler.requests)
        }

    @Test
    fun incrementingTheLastEpisodeCompletesTheEntry() =
        runTest {
            local.put(frieren.copy(episodes = 28), SyncState.SYNCED, WatchStatus.WATCHING, progress = 27)
            val viewModel = viewModel()

            viewModel.onAction(LibraryAction.OnIncrementClick(52991))
            advanceUntilIdle()

            val entry =
                viewModel.state.value.entries
                    .single()
            assertEquals(WatchStatus.COMPLETED, entry.status)
            assertEquals(28, entry.progress)
        }
}

data class SavedEntry(
    val malId: Int,
    val status: WatchStatus,
    val progress: Int,
    val score: Int?,
)

class FakeLibraryLocalDataSource : LibraryLocalDataSource {
    val savedAnime = mutableListOf<Anime>()
    val savedEntries = mutableListOf<SavedEntry>()
    val removedMalIds = mutableListOf<Int>()
    private val known = mutableMapOf<Int, Anime>()
    private val library = MutableStateFlow<List<LibraryEntry>>(emptyList())

    override fun observeLibrary(): Flow<List<LibraryEntry>> = library

    override suspend fun getEntry(malId: Int): LibraryEntry? = library.value.firstOrNull { it.anime.malId == malId }

    override suspend fun saveAnime(anime: Anime) {
        savedAnime += anime
        known[anime.malId] = anime
    }

    override suspend fun saveEntry(
        malId: Int,
        status: WatchStatus,
        progress: Int,
        score: Int?,
    ) {
        savedEntries += SavedEntry(malId, status, progress, score)
        put(known.getValue(malId), SyncState.PENDING, status, progress, score)
    }

    override suspend fun remove(malId: Int) {
        removedMalIds += malId
        library.update { entries -> entries.filterNot { it.anime.malId == malId } }
    }

    fun put(
        anime: Anime,
        syncState: SyncState,
        status: WatchStatus = WatchStatus.PLAN_TO_WATCH,
        progress: Int = 0,
        score: Int? = null,
    ) {
        known[anime.malId] = anime
        library.update { entries ->
            entries.filterNot { it.anime.malId == anime.malId } +
                LibraryEntry(
                    "entry-${anime.malId}",
                    anime,
                    status,
                    progress,
                    score,
                    Instant.fromEpochMilliseconds(0),
                    syncState,
                )
        }
    }
}
