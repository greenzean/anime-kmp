package com.greenzean.animekmp.shared.library.presentation

import app.cash.turbine.test
import com.greenzean.animekmp.shared.auth.FakeSyncScheduler
import com.greenzean.animekmp.shared.library.domain.Anime
import com.greenzean.animekmp.shared.library.domain.EditEntryUseCase
import com.greenzean.animekmp.shared.library.domain.LibraryEntry
import com.greenzean.animekmp.shared.library.domain.LibraryLocalDataSource
import com.greenzean.animekmp.shared.library.domain.LibrarySort
import com.greenzean.animekmp.shared.library.domain.ObserveLibraryIsEmptyUseCase
import com.greenzean.animekmp.shared.library.domain.ObserveLibrarySortUseCase
import com.greenzean.animekmp.shared.library.domain.ObserveLibraryUseCase
import com.greenzean.animekmp.shared.library.domain.SetLibrarySortUseCase
import com.greenzean.animekmp.shared.library.domain.SyncState
import com.greenzean.animekmp.shared.library.domain.WatchStatus
import com.greenzean.animekmp.shared.settings.FakeDisplaySettings
import com.greenzean.animekmp.shared.settings.FakeLibrarySettings
import com.greenzean.animekmp.shared.settings.domain.ObserveTitleLanguageUseCase
import com.greenzean.animekmp.shared.settings.domain.TitleLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {
    private val frieren = Anime(52991, "Sousou no Frieren", "Frieren: Beyond Journey's End", "https://img/52991.jpg")
    private val bebop = Anime(1, "Cowboy Bebop")
    private val local = FakeLibraryLocalDataSource()
    private val settings = FakeDisplaySettings()
    private val librarySettings = FakeLibrarySettings()
    private val scheduler = FakeSyncScheduler()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() =
        LibraryViewModel(
            ObserveLibraryUseCase(local),
            ObserveLibraryIsEmptyUseCase(local),
            ObserveLibrarySortUseCase(librarySettings),
            SetLibrarySortUseCase(librarySettings),
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

    private fun LibraryViewModel.titles() = state.value.entries.map { it.title }

    private fun seedThree() {
        local.put(bebop, SyncState.SYNCED, WatchStatus.WATCHING, score = 6, updatedAtMillis = 10)
        local.put(frieren, SyncState.SYNCED, WatchStatus.COMPLETED, score = 9, updatedAtMillis = 30)
        local.put(Anime(9253, "Steins;Gate"), SyncState.SYNCED, WatchStatus.COMPLETED, updatedAtMillis = 20)
    }

    @Test
    fun defaultStateHasNoQueryNoFilterAndLastUpdatedSort() {
        val state = viewModel().state.value

        assertEquals("", state.query)
        assertEquals(null, state.statusFilter)
        assertEquals(LibrarySort.LAST_UPDATED, state.sort)
    }

    @Test
    fun queryChangeNarrowsTheEntriesAndIsReflectedInState() {
        seedThree()
        val viewModel = viewModel()

        viewModel.onAction(LibraryAction.OnQueryChange("bebop"))

        assertEquals(listOf("Cowboy Bebop"), viewModel.titles())
        assertEquals("bebop", viewModel.state.value.query)
    }

    @Test
    fun queryMatchesTheEnglishTitle() {
        seedThree()
        val viewModel = viewModel()

        viewModel.onAction(LibraryAction.OnQueryChange("journey"))

        assertEquals(listOf("Frieren: Beyond Journey's End"), viewModel.titles())
    }

    @Test
    fun surroundingSpacesInTheQueryAreIgnoredForMatching() {
        seedThree()
        val viewModel = viewModel()

        viewModel.onAction(LibraryAction.OnQueryChange("  bebop  "))

        assertEquals(listOf("Cowboy Bebop"), viewModel.titles())
    }

    @Test
    fun clearingTheQueryShowsEverythingAgain() {
        seedThree()
        val viewModel = viewModel()
        viewModel.onAction(LibraryAction.OnQueryChange("bebop"))

        viewModel.onAction(LibraryAction.OnQueryChange(""))

        assertEquals(3, viewModel.state.value.entries.size)
    }

    @Test
    fun statusFilterClickKeepsOnlyThatStatus() {
        seedThree()
        val viewModel = viewModel()

        viewModel.onAction(LibraryAction.OnStatusFilterClick(WatchStatus.COMPLETED))

        assertEquals(WatchStatus.COMPLETED, viewModel.state.value.statusFilter)
        assertEquals(setOf("Steins;Gate", "Frieren: Beyond Journey's End"), viewModel.titles().toSet())
    }

    @Test
    fun clickingTheSelectedStatusAgainSelectsAll() {
        seedThree()
        val viewModel = viewModel()
        viewModel.onAction(LibraryAction.OnStatusFilterClick(WatchStatus.COMPLETED))

        viewModel.onAction(LibraryAction.OnStatusFilterClick(WatchStatus.COMPLETED))

        assertEquals(null, viewModel.state.value.statusFilter)
        assertEquals(3, viewModel.state.value.entries.size)
    }

    @Test
    fun clickingAllSelectsNoStatus() {
        seedThree()
        val viewModel = viewModel()
        viewModel.onAction(LibraryAction.OnStatusFilterClick(WatchStatus.WATCHING))

        viewModel.onAction(LibraryAction.OnStatusFilterClick(null))

        assertEquals(null, viewModel.state.value.statusFilter)
        assertEquals(3, viewModel.state.value.entries.size)
    }

    @Test
    fun clickingAnotherStatusSwitchesTheFilter() {
        seedThree()
        val viewModel = viewModel()
        viewModel.onAction(LibraryAction.OnStatusFilterClick(WatchStatus.COMPLETED))

        viewModel.onAction(LibraryAction.OnStatusFilterClick(WatchStatus.WATCHING))

        assertEquals(WatchStatus.WATCHING, viewModel.state.value.statusFilter)
        assertEquals(listOf("Cowboy Bebop"), viewModel.titles())
    }

    @Test
    fun queryAndStatusFilterCombine() {
        seedThree()
        val viewModel = viewModel()

        viewModel.onAction(LibraryAction.OnStatusFilterClick(WatchStatus.COMPLETED))
        viewModel.onAction(LibraryAction.OnQueryChange("steins"))

        assertEquals(listOf("Steins;Gate"), viewModel.titles())
    }

    @Test
    fun sortChangeReordersTheEntries() {
        seedThree()
        val viewModel = viewModel()
        assertEquals(
            listOf("Frieren: Beyond Journey's End", "Steins;Gate", "Cowboy Bebop"),
            viewModel.titles(),
        )

        viewModel.onAction(LibraryAction.OnSortChange(LibrarySort.TITLE))
        assertEquals(
            listOf("Cowboy Bebop", "Frieren: Beyond Journey's End", "Steins;Gate"),
            viewModel.titles(),
        )
        assertEquals(LibrarySort.TITLE, viewModel.state.value.sort)

        viewModel.onAction(LibraryAction.OnSortChange(LibrarySort.SCORE))
        assertEquals(
            listOf("Frieren: Beyond Journey's End", "Cowboy Bebop", "Steins;Gate"),
            viewModel.titles(),
        )
    }

    @Test
    fun sortChangeIsStoredInSettings() {
        val viewModel = viewModel()

        viewModel.onAction(LibraryAction.OnSortChange(LibrarySort.SCORE))

        assertEquals(LibrarySort.SCORE, librarySettings.sort.value)
    }

    @Test
    fun storedSortIsRestoredByANewViewModel() {
        seedThree()
        librarySettings.setSort(LibrarySort.TITLE)

        val viewModel = viewModel()

        assertEquals(LibrarySort.TITLE, viewModel.state.value.sort)
        assertEquals("Cowboy Bebop", viewModel.titles().first())
    }

    @Test
    fun queryAndStatusAreNotStoredAcrossViewModels() {
        seedThree()
        val first = viewModel()
        first.onAction(LibraryAction.OnQueryChange("bebop"))
        first.onAction(LibraryAction.OnStatusFilterClick(WatchStatus.WATCHING))

        val second = viewModel()

        assertEquals("", second.state.value.query)
        assertEquals(null, second.state.value.statusFilter)
        assertEquals(3, second.state.value.entries.size)
    }

    @Test
    fun emptyLibraryIsFlaggedAsEmpty() {
        assertTrue(viewModel().state.value.isLibraryEmpty)
    }

    @Test
    fun noMatchesKeepsTheLibraryFlaggedAsNotEmpty() {
        seedThree()
        val viewModel = viewModel()

        viewModel.onAction(LibraryAction.OnQueryChange("zzzz"))

        assertTrue(
            viewModel.state.value.entries
                .isEmpty(),
        )
        assertFalse(viewModel.state.value.isLibraryEmpty)
    }

    @Test
    fun noMatchesFromAStatusFilterKeepsTheLibraryFlaggedAsNotEmpty() {
        local.put(bebop, SyncState.SYNCED, WatchStatus.WATCHING)
        val viewModel = viewModel()

        viewModel.onAction(LibraryAction.OnStatusFilterClick(WatchStatus.DROPPED))

        assertTrue(
            viewModel.state.value.entries
                .isEmpty(),
        )
        assertFalse(viewModel.state.value.isLibraryEmpty)
    }

    @Test
    fun removingTheLastEntryFlagsTheLibraryAsEmpty() =
        runTest {
            local.put(bebop, SyncState.SYNCED)
            val viewModel = viewModel()
            assertFalse(viewModel.state.value.isLibraryEmpty)

            local.remove(1)

            assertTrue(viewModel.state.value.isLibraryEmpty)
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

    override fun observeLibrary(
        query: String,
        status: WatchStatus?,
        sort: LibrarySort,
        titleLanguage: TitleLanguage,
    ): Flow<List<LibraryEntry>> =
        library.map { entries ->
            val matching =
                entries.filter { entry ->
                    (status == null || entry.status == status) &&
                        (
                            query.isBlank() ||
                                entry.anime.title.contains(query, ignoreCase = true) ||
                                entry.anime.titleEn?.contains(query, ignoreCase = true) == true
                        )
                }
            when (sort) {
                LibrarySort.LAST_UPDATED -> matching.sortedByDescending { it.updatedAt }
                LibrarySort.TITLE -> matching.sortedBy { displayedTitle(it, titleLanguage).lowercase() }
                LibrarySort.SCORE -> matching.sortedWith(compareByDescending<LibraryEntry> { it.score ?: -1 })
            }
        }

    override fun observeActiveCount(): Flow<Int> = library.map { it.size }

    private fun displayedTitle(
        entry: LibraryEntry,
        language: TitleLanguage,
    ): String = if (language == TitleLanguage.ENGLISH) entry.anime.titleEn ?: entry.anime.title else entry.anime.title

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
        updatedAtMillis: Long = 0,
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
                    Instant.fromEpochMilliseconds(updatedAtMillis),
                    syncState,
                )
        }
    }
}
