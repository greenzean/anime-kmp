package com.greenzean.animekmp.shared.suggestions.presentation

import app.cash.turbine.test
import com.greenzean.animekmp.shared.core.data.createTestDriver
import com.greenzean.animekmp.shared.core.data.db.AnimeDatabase
import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.library.domain.AddToLibraryUseCase
import com.greenzean.animekmp.shared.library.domain.Anime
import com.greenzean.animekmp.shared.library.domain.ObserveLibraryUseCase
import com.greenzean.animekmp.shared.library.domain.SyncState
import com.greenzean.animekmp.shared.library.presentation.FakeLibraryLocalDataSource
import com.greenzean.animekmp.shared.mal.MalAccountHarness
import com.greenzean.animekmp.shared.mal.data.SqlDelightMalCache
import com.greenzean.animekmp.shared.mal.domain.MalAnimeSummary
import com.greenzean.animekmp.shared.mal.domain.MalApiError
import com.greenzean.animekmp.shared.mal.domain.MalSearchPage
import com.greenzean.animekmp.shared.mal.domain.MalSuggestionsApi
import com.greenzean.animekmp.shared.mal.domain.MalSuggestionsRepository
import com.greenzean.animekmp.shared.mal.domain.MalThrottle
import com.greenzean.animekmp.shared.mal.domain.ObserveMalAccountUseCase
import com.greenzean.animekmp.shared.mal.validMalSession
import com.greenzean.animekmp.shared.search.presentation.SearchResultUi
import com.greenzean.animekmp.shared.settings.FakeDisplaySettings
import com.greenzean.animekmp.shared.settings.domain.ObserveTitleLanguageUseCase
import com.greenzean.animekmp.shared.settings.domain.TitleLanguage
import com.greenzean.animekmp.shared.suggestions.domain.GetSuggestionsUseCase
import com.greenzean.animekmp.shared.sync.domain.SyncScheduler
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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

private class FakeSuggestionsFeed : MalSuggestionsApi {
    val offsets = mutableListOf<Int>()
    var gate: CompletableDeferred<Unit>? = null
    var result: Result<MalSearchPage, MalApiError> = Result.Success(MalSearchPage(emptyList(), false))
    val pages = mutableMapOf<Int, Result<MalSearchPage, MalApiError>>()

    override suspend fun suggestions(
        limit: Int,
        offset: Int,
    ): Result<MalSearchPage, MalApiError> {
        offsets += offset
        gate?.await()
        return pages[offset] ?: result
    }
}

private class SuggestionsScheduler : SyncScheduler {
    var requests = 0

    override fun requestSync() {
        requests++
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SuggestionsViewModelTest {
    private val driver = createTestDriver()
    private val feed = FakeSuggestionsFeed()
    private val local = FakeLibraryLocalDataSource()
    private val scheduler = SuggestionsScheduler()
    private val settings = FakeDisplaySettings()
    private val account = MalAccountHarness(validMalSession(), consent = true)
    private val frieren =
        MalAnimeSummary(52991, "Sousou no Frieren", "https://img/52991.jpg", 9.3, "tv", 28, null, "Frieren")
    private val bebop = MalAnimeSummary(1, "Cowboy Bebop", null, 8.7, "tv", 26, null)
    private val naruto = MalAnimeSummary(20, "Naruto", null, 8.0, "tv", 220, null)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        driver.close()
        account.close()
    }

    private fun TestScope.viewModel(): SuggestionsViewModel = viewModelFor(account)

    private fun page(
        vararg items: MalAnimeSummary,
        hasMore: Boolean = false,
    ) = Result.Success(MalSearchPage(items.toList(), hasMore))

    private fun ids(viewModel: SuggestionsViewModel) =
        viewModel.state.value.items
            .map { it.malId }

    @Test
    fun connectedLoadsTheFirstPage() =
        runTest {
            feed.result = page(frieren)
            val viewModel = viewModel()
            advanceUntilIdle()

            val state = viewModel.state.value
            assertEquals(SuggestionsStatus.Loaded, state.status)
            assertEquals(listOf(SearchResultUi(52991, "Frieren", "https://img/52991.jpg", false)), state.items)
            assertEquals(listOf(0), feed.offsets)
        }

    @Test
    fun showsLoadingWhileTheFirstPageIsInFlight() =
        runTest {
            feed.gate = CompletableDeferred()
            feed.result = page(frieren)
            val viewModel = viewModel()
            advanceUntilIdle()

            assertEquals(SuggestionsStatus.Loading, viewModel.state.value.status)

            feed.gate?.complete(Unit)
            advanceUntilIdle()
            assertEquals(SuggestionsStatus.Loaded, viewModel.state.value.status)
        }

    @Test
    fun notConnectedShowsNotConnectedAndMakesNoCall() =
        runTest {
            val loggedOut = MalAccountHarness(null, consent = true)
            val viewModel = viewModelFor(loggedOut)
            advanceUntilIdle()

            assertEquals(SuggestionsStatus.NotConnected, viewModel.state.value.status)
            assertTrue(feed.offsets.isEmpty())
            loggedOut.close()
        }

    @Test
    fun needingConsentIsNotConnected() =
        runTest {
            val needsConsent = MalAccountHarness(null, consent = false)
            val viewModel = viewModelFor(needsConsent)
            advanceUntilIdle()

            assertEquals(SuggestionsStatus.NotConnected, viewModel.state.value.status)
            assertTrue(feed.offsets.isEmpty())
            needsConsent.close()
        }

    @Test
    fun becomingConnectedLoadsTheFirstPage() =
        runTest {
            feed.result = page(frieren)
            val loggedOut = MalAccountHarness(null, consent = true)
            val viewModel = viewModelFor(loggedOut)
            advanceUntilIdle()
            assertEquals(SuggestionsStatus.NotConnected, viewModel.state.value.status)

            loggedOut.tokenStore.save(validMalSession())
            advanceUntilIdle()

            assertEquals(SuggestionsStatus.Loaded, viewModel.state.value.status)
            assertEquals(listOf(52991), ids(viewModel))
            assertEquals(listOf(0), feed.offsets)
            loggedOut.close()
        }

    @Test
    fun disconnectingReturnsToNotConnected() =
        runTest {
            feed.result = page(frieren)
            val viewModel = viewModel()
            advanceUntilIdle()

            account.tokenStore.clear()
            advanceUntilIdle()

            assertEquals(SuggestionsStatus.NotConnected, viewModel.state.value.status)
        }

    @Test
    fun emptyFirstPageIsEmpty() =
        runTest {
            val viewModel = viewModel()
            advanceUntilIdle()

            assertEquals(SuggestionsStatus.Empty, viewModel.state.value.status)
            assertEquals(emptyList(), viewModel.state.value.items)
        }

    @Test
    fun noConnectionIsOffline() =
        runTest {
            feed.result = Result.Error(MalApiError.NO_CONNECTION)
            val viewModel = viewModel()
            advanceUntilIdle()

            assertEquals(SuggestionsStatus.Offline, viewModel.state.value.status)
        }

    @Test
    fun otherFailuresAreErrors() =
        runTest {
            feed.result = Result.Error(MalApiError.SERVER)
            val viewModel = viewModel()
            advanceUntilIdle()

            assertEquals(SuggestionsStatus.Error, viewModel.state.value.status)
        }

    @Test
    fun retryReloadsThePageFromTheStart() =
        runTest {
            feed.result = Result.Error(MalApiError.SERVER)
            val viewModel = viewModel()
            advanceUntilIdle()

            feed.result = page(bebop)
            viewModel.onAction(SuggestionsAction.OnRetryClick)
            advanceUntilIdle()

            assertEquals(SuggestionsStatus.Loaded, viewModel.state.value.status)
            assertEquals(listOf(1), ids(viewModel))
            assertEquals(listOf(0, 0), feed.offsets)
        }

    @Test
    fun retryAfterOfflineLoads() =
        runTest {
            feed.result = Result.Error(MalApiError.NO_CONNECTION)
            val viewModel = viewModel()
            advanceUntilIdle()
            assertEquals(SuggestionsStatus.Offline, viewModel.state.value.status)

            feed.result = page(bebop)
            viewModel.onAction(SuggestionsAction.OnRetryClick)
            advanceUntilIdle()

            assertEquals(SuggestionsStatus.Loaded, viewModel.state.value.status)
        }

    @Test
    fun libraryItemsAreHidden() =
        runTest {
            local.put(Anime(1, "Cowboy Bebop"), SyncState.SYNCED)
            feed.result = page(bebop, frieren)
            val viewModel = viewModel()
            advanceUntilIdle()

            assertEquals(listOf(52991), ids(viewModel))
            assertTrue(
                viewModel.state.value.items
                    .none { it.inLibrary },
            )
        }

    @Test
    fun whenEveryItemIsInTheLibraryTheStatusStaysLoaded() =
        runTest {
            local.put(Anime(1, "Cowboy Bebop"), SyncState.SYNCED)
            feed.result = page(bebop)
            val viewModel = viewModel()
            advanceUntilIdle()

            assertEquals(SuggestionsStatus.Loaded, viewModel.state.value.status)
            assertEquals(emptyList(), viewModel.state.value.items)
        }

    @Test
    fun itemsAddedToTheLibraryElsewhereDisappear() =
        runTest {
            feed.result = page(bebop, frieren)
            val viewModel = viewModel()
            advanceUntilIdle()

            local.put(Anime(1, "Cowboy Bebop"), SyncState.SYNCED)
            advanceUntilIdle()

            assertEquals(listOf(52991), ids(viewModel))
        }

    @Test
    fun removingFromTheLibraryBringsTheItemBack() =
        runTest {
            local.put(Anime(1, "Cowboy Bebop"), SyncState.SYNCED)
            feed.result = page(bebop, frieren)
            val viewModel = viewModel()
            advanceUntilIdle()

            local.remove(1)
            advanceUntilIdle()

            assertEquals(listOf(1, 52991), ids(viewModel))
        }

    @Test
    fun addSavesTheAnimeAndHidesIt() =
        runTest {
            feed.result = page(frieren, bebop)
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.onAction(SuggestionsAction.OnAddClick(52991))
            advanceUntilIdle()

            assertEquals(listOf(1), ids(viewModel))
            val saved = local.savedAnime.single()
            assertEquals("Sousou no Frieren", saved.title)
            assertEquals("Frieren", saved.titleEn)
            assertEquals("https://img/52991.jpg", saved.imageUrl)
            assertEquals(1, scheduler.requests)
        }

    @Test
    fun addingAnUnknownIdDoesNothing() =
        runTest {
            feed.result = page(frieren)
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.onAction(SuggestionsAction.OnAddClick(999))
            advanceUntilIdle()

            assertTrue(local.savedAnime.isEmpty())
            assertEquals(0, scheduler.requests)
        }

    @Test
    fun loadMoreAppendsTheNextPageWithoutDuplicates() =
        runTest {
            feed.pages[0] = page(bebop, frieren, hasMore = true)
            feed.pages[2] = page(frieren, naruto, hasMore = false)
            val viewModel = viewModel()
            advanceUntilIdle()
            assertTrue(viewModel.state.value.canLoadMore)

            viewModel.onAction(SuggestionsAction.OnLoadMore)
            advanceUntilIdle()

            assertEquals(listOf(1, 52991, 20), ids(viewModel))
            assertFalse(viewModel.state.value.canLoadMore)
            assertEquals(listOf(0, 2), feed.offsets)
        }

    @Test
    fun nextOffsetCountsLibraryHiddenItems() =
        runTest {
            local.put(Anime(1, "Cowboy Bebop"), SyncState.SYNCED)
            feed.pages[0] = page(bebop, frieren, hasMore = true)
            feed.pages[2] = page(naruto)
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.onAction(SuggestionsAction.OnLoadMore)
            advanceUntilIdle()

            assertEquals(listOf(0, 2), feed.offsets)
            assertEquals(listOf(52991, 20), ids(viewModel))
        }

    @Test
    fun nextOffsetCountsDuplicatesToo() =
        runTest {
            feed.pages[0] = page(bebop, frieren, hasMore = true)
            feed.pages[2] = page(frieren, naruto, hasMore = true)
            feed.pages[4] = page(bebop, hasMore = false)
            val viewModel = viewModel()
            advanceUntilIdle()
            viewModel.onAction(SuggestionsAction.OnLoadMore)
            advanceUntilIdle()

            viewModel.onAction(SuggestionsAction.OnLoadMore)
            advanceUntilIdle()

            assertEquals(listOf(0, 2, 4), feed.offsets)
            assertEquals(listOf(1, 52991, 20), ids(viewModel))
        }

    @Test
    fun anEmptyLaterPageDoesNotTurnIntoEmpty() =
        runTest {
            feed.pages[0] = page(bebop, hasMore = true)
            feed.pages[1] = page(hasMore = false)
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.onAction(SuggestionsAction.OnLoadMore)
            advanceUntilIdle()

            assertEquals(SuggestionsStatus.Loaded, viewModel.state.value.status)
            assertEquals(listOf(1), ids(viewModel))
            assertFalse(viewModel.state.value.canLoadMore)
        }

    @Test
    fun itemsFollowTheTitleLanguage() =
        runTest {
            feed.result = page(frieren)
            val viewModel = viewModel()
            advanceUntilIdle()

            settings.setTitleLanguage(TitleLanguage.MAIN)
            advanceUntilIdle()

            assertEquals(
                "Sousou no Frieren",
                viewModel.state.value.items
                    .single()
                    .title,
            )
        }

    @Test
    fun itemClickEmitsNavigateToDetails() =
        runTest {
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.events.test {
                viewModel.onAction(SuggestionsAction.OnItemClick(52991))
                advanceUntilIdle()

                assertEquals(SuggestionsEvent.NavigateToDetails(52991), awaitItem())
            }
        }

    private fun TestScope.viewModelFor(harness: MalAccountHarness): SuggestionsViewModel {
        val repository =
            MalSuggestionsRepository(
                feed,
                SqlDelightMalCache(AnimeDatabase(driver), UnconfinedTestDispatcher(testScheduler)),
                MalThrottle(),
            )
        val viewModel =
            SuggestionsViewModel(
                ObserveMalAccountUseCase(harness.auth, harness.accountStore),
                GetSuggestionsUseCase(repository),
                ObserveLibraryUseCase(local),
                AddToLibraryUseCase(local, scheduler),
                ObserveTitleLanguageUseCase(settings),
            )
        backgroundScope.launch { viewModel.state.collect {} }
        return viewModel
    }
}
