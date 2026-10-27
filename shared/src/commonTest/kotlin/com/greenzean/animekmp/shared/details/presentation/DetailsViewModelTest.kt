package com.greenzean.animekmp.shared.details.presentation

import app.cash.turbine.test
import com.greenzean.animekmp.shared.core.data.createTestDriver
import com.greenzean.animekmp.shared.core.data.db.AnimeDatabase
import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.details.domain.GetAnimeDetailsUseCase
import com.greenzean.animekmp.shared.library.domain.AddToLibraryUseCase
import com.greenzean.animekmp.shared.library.domain.Anime
import com.greenzean.animekmp.shared.library.domain.ObserveLibraryUseCase
import com.greenzean.animekmp.shared.library.domain.SyncState
import com.greenzean.animekmp.shared.library.presentation.FakeLibraryLocalDataSource
import com.greenzean.animekmp.shared.mal.data.SqlDelightMalCache
import com.greenzean.animekmp.shared.mal.domain.MalAnimeApi
import com.greenzean.animekmp.shared.mal.domain.MalAnimeDetails
import com.greenzean.animekmp.shared.mal.domain.MalAnimeRepository
import com.greenzean.animekmp.shared.mal.domain.MalAnimeSummary
import com.greenzean.animekmp.shared.mal.domain.MalApiError
import com.greenzean.animekmp.shared.mal.domain.MalSearchPage
import com.greenzean.animekmp.shared.mal.domain.MalThrottle
import com.greenzean.animekmp.shared.settings.FakeDisplaySettings
import com.greenzean.animekmp.shared.settings.domain.ObserveTitleLanguageUseCase
import com.greenzean.animekmp.shared.settings.domain.TitleLanguage
import com.greenzean.animekmp.shared.sync.domain.SyncScheduler
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

private class FakeDetailsApi : MalAnimeApi {
    var calls = 0
    var result: Result<MalAnimeDetails, MalApiError> = Result.Error(MalApiError.UNKNOWN)
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun search(
        query: String,
        limit: Int,
        offset: Int,
    ): Result<MalSearchPage, MalApiError> = Result.Success(MalSearchPage(emptyList(), false))

    override suspend fun details(malId: Int): Result<MalAnimeDetails, MalApiError> {
        calls++
        gate?.await()
        return result
    }
}

private class CountingScheduler : SyncScheduler {
    var requests = 0

    override fun requestSync() {
        requests++
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class DetailsViewModelTest {
    private val driver = createTestDriver()
    private val api = FakeDetailsApi()
    private val local = FakeLibraryLocalDataSource()
    private val scheduler = CountingScheduler()
    private val settings = FakeDisplaySettings()
    private val frieren =
        MalAnimeDetails(
            summary =
                MalAnimeSummary(
                    52991,
                    "Sousou no Frieren",
                    "https://img/52991.jpg",
                    9.3,
                    "tv",
                    28,
                    "finished_airing",
                    "Frieren",
                ),
            synopsis = "After the party of heroes defeated the Demon King.",
            genres = listOf("Adventure", "Drama"),
            startDate = "2023-09-29",
            rank = 1,
            recommendations = emptyList(),
        )

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        driver.close()
    }

    private fun TestScope.cache() = SqlDelightMalCache(AnimeDatabase(driver), UnconfinedTestDispatcher(testScheduler))

    private fun TestScope.viewModel(malId: Int = 52991): DetailsViewModel =
        DetailsViewModel(
            malId,
            GetAnimeDetailsUseCase(MalAnimeRepository(api, cache(), MalThrottle())),
            ObserveLibraryUseCase(local),
            AddToLibraryUseCase(local, scheduler),
            ObserveTitleLanguageUseCase(settings),
        )

    @Test
    fun backClickEmitsNavigateBack() =
        runTest {
            api.result = Result.Success(frieren)
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.events.test {
                viewModel.onAction(DetailsAction.OnBackClick)
                advanceUntilIdle()

                assertEquals(DetailsEvent.NavigateBack, awaitItem())
            }
        }

    @Test
    fun staysLoadingUntilTheDetailsArrive() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            api.gate = gate
            api.result = Result.Success(frieren)
            val viewModel = viewModel()
            advanceUntilIdle()

            assertEquals(DetailsStatus.Loading, viewModel.state.value.status)
            assertNull(viewModel.state.value.details)
            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(DetailsStatus.Loaded, viewModel.state.value.status)
        }

    @Test
    fun retryGoesBackToLoadingBeforeItLoads() =
        runTest {
            api.result = Result.Error(MalApiError.SERVER)
            val viewModel = viewModel()
            advanceUntilIdle()
            assertEquals(DetailsStatus.Error, viewModel.state.value.status)

            val gate = CompletableDeferred<Unit>()
            api.gate = gate
            api.result = Result.Success(frieren)
            viewModel.onAction(DetailsAction.OnRetryClick)
            advanceUntilIdle()
            assertEquals(DetailsStatus.Loading, viewModel.state.value.status)

            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(DetailsStatus.Loaded, viewModel.state.value.status)
        }

    @Test
    fun startDatesAreParsed() =
        runTest {
            api.result = Result.Success(frieren)
            val viewModel = viewModel()
            advanceUntilIdle()

            assertEquals(
                MalDate(2023, 9, 29),
                viewModel.state.value.details
                    ?.startDate,
            )
        }

    @Test
    fun partialStartDatesKeepTheirPrecision() {
        assertEquals(MalDate(2027, 10, null), parseMalDate("2027-10"))
        assertEquals(MalDate(2024, null, null), parseMalDate("2024"))
        assertNull(parseMalDate("unknown"))
    }

    @Test
    fun invalidMonthsAndOrphanDaysAreDropped() {
        assertEquals(MalDate(2024, null, null), parseMalDate("2024-13-05"))
        assertEquals(MalDate(2024, null, null), parseMalDate("2024-00"))
        assertEquals(MalDate(2024, 2, 30), parseMalDate("2024-02-30"))
    }

    @Test
    fun animeAlreadyInTheLibraryOpensAsInLibrary() =
        runTest {
            local.put(Anime(52991, "Sousou no Frieren", "Frieren", "https://img/52991.jpg"), SyncState.SYNCED)
            api.result = Result.Success(frieren)
            val viewModel = viewModel()
            advanceUntilIdle()

            assertTrue(viewModel.state.value.inLibrary)
        }

    @Test
    fun freshCachedDetailsSkipTheApi() =
        runTest {
            cache().saveDetails(52991, frieren, Clock.System.now())
            val viewModel = viewModel()
            advanceUntilIdle()

            assertEquals(DetailsStatus.Loaded, viewModel.state.value.status)
            assertEquals(0, api.calls)
        }

    @Test
    fun notFoundHasItsOwnStatus() =
        runTest {
            api.result = Result.Error(MalApiError.NOT_FOUND)
            val viewModel = viewModel()
            advanceUntilIdle()

            assertEquals(DetailsStatus.NotFound, viewModel.state.value.status)
        }

    @Test
    fun throttledHasItsOwnStatus() =
        runTest {
            api.result = Result.Error(MalApiError.THROTTLED)
            val viewModel = viewModel()
            advanceUntilIdle()

            assertEquals(DetailsStatus.Throttled, viewModel.state.value.status)
        }

    @Test
    fun twoQuickAddTapsAddOnce() =
        runTest {
            api.result = Result.Success(frieren)
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.onAction(DetailsAction.OnAddClick)
            viewModel.onAction(DetailsAction.OnAddClick)
            advanceUntilIdle()

            assertEquals(1, scheduler.requests)
        }

    @Test
    fun showsTheLoadedDetails() =
        runTest {
            api.result = Result.Success(frieren)
            val viewModel = viewModel()
            advanceUntilIdle()

            val state = viewModel.state.value
            val details = state.details!!
            assertEquals(DetailsStatus.Loaded, state.status)
            assertEquals("Frieren", details.title)
            assertEquals("Sousou no Frieren", details.otherTitle)
            assertEquals(28, details.episodes)
            assertEquals(9.3, details.meanScore)
            assertEquals(listOf("Adventure", "Drama"), details.genres)
            assertEquals(AiringStatus.Finished, details.airingStatus)
            assertEquals(MalDate(2023, 9, 29), details.startDate)
        }

    @Test
    fun titlesFollowTheTitleLanguage() =
        runTest {
            api.result = Result.Success(frieren)
            val viewModel = viewModel()
            advanceUntilIdle()

            settings.setTitleLanguage(TitleLanguage.MAIN)
            advanceUntilIdle()

            val details = viewModel.state.value.details!!
            assertEquals("Sousou no Frieren", details.title)
            assertEquals("Frieren", details.otherTitle)
        }

    @Test
    fun noConnectionWithNothingCachedIsOffline() =
        runTest {
            api.result = Result.Error(MalApiError.NO_CONNECTION)
            val viewModel = viewModel()
            advanceUntilIdle()

            assertEquals(DetailsStatus.Offline, viewModel.state.value.status)
            assertNull(viewModel.state.value.details)
        }

    @Test
    fun noConnectionWithCachedDetailsStillShowsThem() =
        runTest {
            cache().saveDetails(52991, frieren, Instant.fromEpochMilliseconds(0))
            api.result = Result.Error(MalApiError.NO_CONNECTION)
            val viewModel = viewModel()
            advanceUntilIdle()

            assertEquals(DetailsStatus.Loaded, viewModel.state.value.status)
            assertEquals(
                "Frieren",
                viewModel.state.value.details
                    ?.title,
            )
        }

    @Test
    fun otherFailuresAreAnError() =
        runTest {
            api.result = Result.Error(MalApiError.SERVER)
            val viewModel = viewModel()
            advanceUntilIdle()

            assertEquals(DetailsStatus.Error, viewModel.state.value.status)
        }

    @Test
    fun retryLoadsAgain() =
        runTest {
            api.result = Result.Error(MalApiError.NO_CONNECTION)
            val viewModel = viewModel()
            advanceUntilIdle()

            api.result = Result.Success(frieren)
            viewModel.onAction(DetailsAction.OnRetryClick)
            advanceUntilIdle()

            assertEquals(DetailsStatus.Loaded, viewModel.state.value.status)
            assertEquals(2, api.calls)
        }

    @Test
    fun addPutsTheAnimeInTheLibrary() =
        runTest {
            api.result = Result.Success(frieren)
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.onAction(DetailsAction.OnAddClick)
            advanceUntilIdle()

            assertTrue(viewModel.state.value.inLibrary)
            val saved = local.savedAnime.single()
            assertEquals(52991, saved.malId)
            assertEquals("Sousou no Frieren", saved.title)
            assertEquals("Frieren", saved.titleEn)
            assertEquals(1, scheduler.requests)
        }

    @Test
    fun addingTwiceDoesNothingTheSecondTime() =
        runTest {
            api.result = Result.Success(frieren)
            val viewModel = viewModel()
            advanceUntilIdle()
            viewModel.onAction(DetailsAction.OnAddClick)
            advanceUntilIdle()

            viewModel.onAction(DetailsAction.OnAddClick)
            advanceUntilIdle()

            assertEquals(1, scheduler.requests)
        }

    @Test
    fun addDoesNothingBeforeTheDetailsLoad() =
        runTest {
            api.result = Result.Error(MalApiError.SERVER)
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.onAction(DetailsAction.OnAddClick)
            advanceUntilIdle()

            assertEquals(0, scheduler.requests)
        }
}
