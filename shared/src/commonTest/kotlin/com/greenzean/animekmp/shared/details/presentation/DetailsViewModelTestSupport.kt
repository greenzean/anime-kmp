package com.greenzean.animekmp.shared.details.presentation

import com.greenzean.animekmp.shared.core.data.createTestDriver
import com.greenzean.animekmp.shared.core.data.db.AnimeDatabase
import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.details.domain.GetAnimeDetailsUseCase
import com.greenzean.animekmp.shared.library.domain.AddToLibraryUseCase
import com.greenzean.animekmp.shared.library.domain.Anime
import com.greenzean.animekmp.shared.library.domain.EditEntryUseCase
import com.greenzean.animekmp.shared.library.domain.ObserveLibraryUseCase
import com.greenzean.animekmp.shared.library.domain.RemoveFromLibraryUseCase
import com.greenzean.animekmp.shared.library.domain.RestoreEntryUseCase
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
import com.greenzean.animekmp.shared.sync.domain.SyncScheduler
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.time.Clock
import kotlin.time.Instant

class FakeDetailsApi : MalAnimeApi {
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

class FixedClock(
    private val instant: Instant,
) : Clock {
    override fun now(): Instant = instant
}

class CountingScheduler : SyncScheduler {
    var requests = 0

    override fun requestSync() {
        requests++
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
abstract class DetailsViewModelTestBase {
    protected val driver = createTestDriver()
    protected val api = FakeDetailsApi()
    protected val local = FakeLibraryLocalDataSource()
    protected val scheduler = CountingScheduler()
    protected val settings = FakeDisplaySettings()
    protected val frieren =
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

    protected fun TestScope.cache() = SqlDelightMalCache(AnimeDatabase(driver), UnconfinedTestDispatcher(testScheduler))

    protected fun TestScope.viewModel(malId: Int = 52991): DetailsViewModel =
        DetailsViewModel(
            malId,
            GetAnimeDetailsUseCase(MalAnimeRepository(api, cache(), MalThrottle())),
            ObserveLibraryUseCase(local),
            AddToLibraryUseCase(local, scheduler),
            ObserveTitleLanguageUseCase(settings),
            EditEntryUseCase(local, scheduler),
            RemoveFromLibraryUseCase(local, scheduler),
            RestoreEntryUseCase(local, scheduler),
            clock = FixedClock(Instant.parse("2023-10-13T12:00:00Z")),
        )

    protected val frierenAnime = Anime(52991, "Sousou no Frieren", "Frieren", "https://img/52991.jpg")
}
