package com.greenzean.animekmp.shared.library.presentation

import app.cash.turbine.test
import com.greenzean.animekmp.shared.library.domain.Anime
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

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = LibraryViewModel(ObserveLibraryUseCase(local), ObserveTitleLanguageUseCase(settings))

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
}

class FakeLibraryLocalDataSource : LibraryLocalDataSource {
    val savedAnime = mutableListOf<Anime>()
    private val library = MutableStateFlow<List<LibraryEntry>>(emptyList())

    override fun observeLibrary(): Flow<List<LibraryEntry>> = library

    override suspend fun saveAnime(anime: Anime) {
        savedAnime += anime
    }

    override suspend fun setStatus(
        malId: Int,
        status: WatchStatus,
    ) {
        val anime = savedAnime.last { it.malId == malId }
        put(anime, SyncState.PENDING, status)
    }

    override suspend fun remove(malId: Int) {
        library.update { entries -> entries.filterNot { it.anime.malId == malId } }
    }

    fun put(
        anime: Anime,
        syncState: SyncState,
        status: WatchStatus = WatchStatus.PLAN_TO_WATCH,
    ) {
        library.update { entries ->
            entries.filterNot { it.anime.malId == anime.malId } +
                LibraryEntry("entry-${anime.malId}", anime, status, Instant.fromEpochMilliseconds(0), syncState)
        }
    }
}
