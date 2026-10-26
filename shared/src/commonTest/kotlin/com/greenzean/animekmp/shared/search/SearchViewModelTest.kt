package com.greenzean.animekmp.shared.search

import com.greenzean.animekmp.shared.core.data.createTestDriver
import com.greenzean.animekmp.shared.core.data.db.AnimeDatabase
import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.library.domain.AddToLibraryUseCase
import com.greenzean.animekmp.shared.library.domain.ObserveLibraryUseCase
import com.greenzean.animekmp.shared.library.presentation.FakeLibraryLocalDataSource
import com.greenzean.animekmp.shared.mal.data.SqlDelightMalCache
import com.greenzean.animekmp.shared.mal.domain.MalAnimeApi
import com.greenzean.animekmp.shared.mal.domain.MalAnimeDetails
import com.greenzean.animekmp.shared.mal.domain.MalAnimeRepository
import com.greenzean.animekmp.shared.mal.domain.MalAnimeSummary
import com.greenzean.animekmp.shared.mal.domain.MalApiError
import com.greenzean.animekmp.shared.mal.domain.MalSearchPage
import com.greenzean.animekmp.shared.mal.domain.MalThrottle
import com.greenzean.animekmp.shared.search.presentation.SearchAction
import com.greenzean.animekmp.shared.search.presentation.SearchResultUi
import com.greenzean.animekmp.shared.search.presentation.SearchStatus
import com.greenzean.animekmp.shared.search.presentation.SearchViewModel
import com.greenzean.animekmp.shared.settings.FakeDisplaySettings
import com.greenzean.animekmp.shared.settings.domain.TitleLanguage
import com.greenzean.animekmp.shared.sync.domain.SyncScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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

private class FakeSearchApi : MalAnimeApi {
    val searches = mutableListOf<Pair<String, Int>>()
    var result: Result<MalSearchPage, MalApiError> = Result.Success(MalSearchPage(emptyList(), false))
    val pages = mutableMapOf<Int, MalSearchPage>()

    override suspend fun search(
        query: String,
        limit: Int,
        offset: Int,
    ): Result<MalSearchPage, MalApiError> {
        searches += query to offset
        return pages[offset]?.let { Result.Success(it) } ?: result
    }

    override suspend fun details(malId: Int): Result<MalAnimeDetails, MalApiError> = Result.Error(MalApiError.UNKNOWN)
}

private class CountingScheduler : SyncScheduler {
    var requests = 0

    override fun requestSync() {
        requests++
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val driver = createTestDriver()
    private val api = FakeSearchApi()
    private val local = FakeLibraryLocalDataSource()
    private val scheduler = CountingScheduler()
    private val settings = FakeDisplaySettings()
    private val frieren =
        MalAnimeSummary(52991, "Sousou no Frieren", "https://img/52991.jpg", 9.3, "tv", 28, null, "Frieren")
    private val bebop = MalAnimeSummary(1, "Cowboy Bebop", null, 8.7, "tv", 26, null)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        driver.close()
    }

    private fun TestScope.viewModel(): SearchViewModel {
        val repository =
            MalAnimeRepository(
                api,
                SqlDelightMalCache(AnimeDatabase(driver), UnconfinedTestDispatcher(testScheduler)),
                MalThrottle(),
            )
        return SearchViewModel(
            repository,
            ObserveLibraryUseCase(local),
            AddToLibraryUseCase(local, scheduler),
            settings,
        )
    }

    private fun page(
        vararg items: MalAnimeSummary,
        hasMore: Boolean = false,
    ) = Result.Success(MalSearchPage(items.toList(), hasMore))

    @Test
    fun startsIdle() =
        runTest {
            val state = viewModel().state.value

            assertEquals(SearchStatus.Idle, state.status)
            assertEquals("", state.query)
        }

    @Test
    fun shortQueriesStayIdleAndMakeNoCall() =
        runTest {
            val viewModel = viewModel()

            viewModel.onAction(SearchAction.OnQueryChange("be"))
            advanceUntilIdle()

            assertEquals(SearchStatus.Idle, viewModel.state.value.status)
            assertEquals("be", viewModel.state.value.query)
            assertTrue(api.searches.isEmpty())
        }

    @Test
    fun searchWaitsForTheDebounceAndShowsLoadingThenResults() =
        runTest {
            api.result = page(frieren)
            val viewModel = viewModel()

            viewModel.onAction(SearchAction.OnQueryChange("frieren"))
            advanceTimeBy(399)
            assertTrue(api.searches.isEmpty())
            advanceTimeBy(2)
            advanceUntilIdle()

            val state = viewModel.state.value
            assertEquals(SearchStatus.Results, state.status)
            assertEquals(listOf(SearchResultUi(52991, "Frieren", "https://img/52991.jpg", false)), state.results)
            assertEquals(listOf("frieren" to 0), api.searches)
        }

    @Test
    fun onlyTheLastQueryIsSearched() =
        runTest {
            api.result = page(bebop)
            val viewModel = viewModel()

            viewModel.onAction(SearchAction.OnQueryChange("beb"))
            advanceTimeBy(200)
            viewModel.onAction(SearchAction.OnQueryChange("bebop"))
            advanceUntilIdle()

            assertEquals(listOf("bebop" to 0), api.searches)
        }

    @Test
    fun noMatchesIsEmpty() =
        runTest {
            val viewModel = viewModel()

            viewModel.onAction(SearchAction.OnQueryChange("zzzzzz"))
            advanceUntilIdle()

            assertEquals(SearchStatus.Empty, viewModel.state.value.status)
        }

    @Test
    fun noConnectionIsOffline() =
        runTest {
            api.result = Result.Error(MalApiError.NO_CONNECTION)
            val viewModel = viewModel()

            viewModel.onAction(SearchAction.OnQueryChange("bebop"))
            advanceUntilIdle()

            assertEquals(SearchStatus.Offline, viewModel.state.value.status)
        }

    @Test
    fun otherFailuresAreErrorsAndRetryRunsTheQueryAgain() =
        runTest {
            api.result = Result.Error(MalApiError.SERVER)
            val viewModel = viewModel()
            viewModel.onAction(SearchAction.OnQueryChange("bebop"))
            advanceUntilIdle()
            assertEquals(SearchStatus.Error, viewModel.state.value.status)

            api.result = page(bebop)
            viewModel.onAction(SearchAction.OnRetryClick)
            advanceUntilIdle()

            assertEquals(SearchStatus.Results, viewModel.state.value.status)
            assertEquals(2, api.searches.size)
        }

    @Test
    fun clearingTheQueryReturnsToIdle() =
        runTest {
            api.result = page(bebop)
            val viewModel = viewModel()
            viewModel.onAction(SearchAction.OnQueryChange("bebop"))
            advanceUntilIdle()

            viewModel.onAction(SearchAction.OnQueryChange(""))
            advanceUntilIdle()

            assertEquals(SearchStatus.Idle, viewModel.state.value.status)
            assertEquals(emptyList(), viewModel.state.value.results)
        }

    @Test
    fun loadMoreAppendsTheNextPageWithoutDuplicates() =
        runTest {
            api.pages[0] = MalSearchPage(listOf(bebop), hasMore = true)
            api.pages[1] = MalSearchPage(listOf(bebop, frieren), hasMore = false)
            val viewModel = viewModel()
            viewModel.onAction(SearchAction.OnQueryChange("anime"))
            advanceUntilIdle()
            assertTrue(viewModel.state.value.canLoadMore)

            viewModel.onAction(SearchAction.OnLoadMore)
            advanceUntilIdle()

            assertEquals(
                listOf(1, 52991),
                viewModel.state.value.results
                    .map { it.malId },
            )
            assertFalse(viewModel.state.value.canLoadMore)
            assertEquals(listOf("anime" to 0, "anime" to 1), api.searches)
        }

    @Test
    fun addSavesTheAnimeAsPlanToWatchAndMarksItInLibrary() =
        runTest {
            api.result = page(frieren)
            val viewModel = viewModel()
            viewModel.onAction(SearchAction.OnQueryChange("frieren"))
            advanceUntilIdle()

            viewModel.onAction(SearchAction.OnAddClick(52991))
            advanceUntilIdle()

            assertTrue(
                viewModel.state.value.results
                    .single()
                    .inLibrary,
            )
            val saved = local.savedAnime.single()
            assertEquals("Sousou no Frieren", saved.title)
            assertEquals("Frieren", saved.titleEn)
            assertEquals("https://img/52991.jpg", saved.imageUrl)
            assertEquals(1, scheduler.requests)
        }

    @Test
    fun addingAnAnimeAlreadyInTheLibraryDoesNothing() =
        runTest {
            api.result = page(frieren)
            val viewModel = viewModel()
            viewModel.onAction(SearchAction.OnQueryChange("frieren"))
            advanceUntilIdle()
            viewModel.onAction(SearchAction.OnAddClick(52991))
            advanceUntilIdle()

            viewModel.onAction(SearchAction.OnAddClick(52991))
            advanceUntilIdle()

            assertEquals(1, scheduler.requests)
        }

    @Test
    fun resultsFollowTheTitleLanguage() =
        runTest {
            api.result = page(frieren)
            val viewModel = viewModel()
            viewModel.onAction(SearchAction.OnQueryChange("frieren"))
            advanceUntilIdle()

            settings.setTitleLanguage(TitleLanguage.MAIN)
            advanceUntilIdle()

            assertEquals(
                "Sousou no Frieren",
                viewModel.state.value.results
                    .single()
                    .title,
            )
        }
}
