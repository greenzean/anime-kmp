package com.greenzean.animekmp.shared.sync

import com.greenzean.animekmp.shared.core.data.createTestDriver
import com.greenzean.animekmp.shared.core.data.db.AnimeDatabase
import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.library.data.SqlDelightLibraryDataSource
import com.greenzean.animekmp.shared.library.domain.WatchStatus
import com.greenzean.animekmp.shared.mal.data.SqlDelightMalCache
import com.greenzean.animekmp.shared.mal.domain.MalAnimeApi
import com.greenzean.animekmp.shared.mal.domain.MalAnimeDetails
import com.greenzean.animekmp.shared.mal.domain.MalAnimeRepository
import com.greenzean.animekmp.shared.mal.domain.MalAnimeSummary
import com.greenzean.animekmp.shared.mal.domain.MalApiError
import com.greenzean.animekmp.shared.mal.domain.MalSearchPage
import com.greenzean.animekmp.shared.mal.domain.MalThrottle
import com.greenzean.animekmp.shared.sync.data.SqlDelightSyncLocalDataSource
import com.greenzean.animekmp.shared.sync.domain.MalParkedAnimeResolver
import com.greenzean.animekmp.shared.sync.domain.SyncChange
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

private class FakeDetailsApi : MalAnimeApi {
    val details = mutableMapOf<Int, Result<MalAnimeDetails, MalApiError>>()
    val requested = mutableListOf<Int>()

    override suspend fun search(
        query: String,
        limit: Int,
        offset: Int,
    ): Result<MalSearchPage, MalApiError> = Result.Success(MalSearchPage(emptyList(), false))

    override suspend fun details(malId: Int): Result<MalAnimeDetails, MalApiError> {
        requested += malId
        return details[malId] ?: Result.Error(MalApiError.NOT_FOUND)
    }
}

class ParkedAnimeResolverTest {
    private val driver = createTestDriver()
    private val db = AnimeDatabase(driver)
    private val dispatcher = UnconfinedTestDispatcher()
    private val sync = SqlDelightSyncLocalDataSource(db, dispatcher)
    private val library = SqlDelightLibraryDataSource(db, dispatcher = dispatcher)
    private val api = FakeDetailsApi()
    private val mal = MalAnimeRepository(api, SqlDelightMalCache(db, dispatcher), MalThrottle())
    private val resolver = MalParkedAnimeResolver(sync, library, mal)

    @AfterTest
    fun tearDown() = driver.close()

    private fun details(
        malId: Int,
        title: String,
    ) = Result.Success(
        MalAnimeDetails(
            MalAnimeSummary(malId, title, "https://img/$malId.jpg", null, null, null, null, "$title EN"),
            null,
            emptyList(),
            null,
            null,
            emptyList(),
        ),
    )

    private suspend fun park(malId: Int) {
        sync.applyPage(
            listOf(SyncChange("entry-$malId", malId, WatchStatus.WATCHING, Instant.fromEpochMilliseconds(5), null)),
            "cursor",
        )
    }

    @Test
    fun parkedAnimeGetsItsTitleAndImageFromMalAndTheEntryIsApplied() =
        runTest {
            park(52991)
            api.details[52991] = details(52991, "Frieren")

            resolver.resolve()
            val applied = sync.retryParked()

            val row = db.animeQueries.selectByMalId(52991).executeAsOne()
            assertEquals("Frieren", row.title)
            assertEquals("Frieren EN", row.title_en)
            assertEquals("https://img/52991.jpg", row.image_url)
            assertEquals(1, applied)
            assertEquals(
                "entry-52991",
                db.listEntryQueries
                    .selectByMalId(52991)
                    .executeAsOne()
                    .id,
            )
        }

    @Test
    fun malFailureLeavesTheChangeParkedForTheNextSync() =
        runTest {
            park(52991)
            api.details[52991] = Result.Error(MalApiError.NO_CONNECTION)

            resolver.resolve()
            val applied = sync.retryParked()

            assertEquals(0, applied)
            assertNull(db.animeQueries.selectByMalId(52991).executeAsOneOrNull())
            assertEquals(listOf(52991), sync.parkedMissingAnime())
        }

    @Test
    fun noConnectionStopsAfterTheFirstFailure() =
        runTest {
            park(1)
            park(2)
            api.details[1] = Result.Error(MalApiError.NO_CONNECTION)

            resolver.resolve()

            assertEquals(1, api.requested.size)
        }

    @Test
    fun animeMalDoesNotKnowIsSkippedAndTheRestIsResolved() =
        runTest {
            park(1)
            park(2)
            api.details[2] = details(2, "Second")

            resolver.resolve()

            assertEquals(listOf(1), sync.parkedMissingAnime())
            assertEquals(
                "Second",
                db.animeQueries
                    .selectByMalId(2)
                    .executeAsOne()
                    .title,
            )
        }
}
