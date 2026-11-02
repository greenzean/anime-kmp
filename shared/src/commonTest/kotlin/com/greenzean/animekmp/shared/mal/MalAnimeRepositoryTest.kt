package com.greenzean.animekmp.shared.mal

import com.greenzean.animekmp.shared.core.data.createTestDriver
import com.greenzean.animekmp.shared.core.data.db.AnimeDatabase
import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.core.logging.LogLevel
import com.greenzean.animekmp.shared.core.logging.RecordingLogSink
import com.greenzean.animekmp.shared.core.logging.SinkLogger
import com.greenzean.animekmp.shared.mal.data.SqlDelightMalCache
import com.greenzean.animekmp.shared.mal.domain.MalAnimeApi
import com.greenzean.animekmp.shared.mal.domain.MalAnimeDetails
import com.greenzean.animekmp.shared.mal.domain.MalAnimeRepository
import com.greenzean.animekmp.shared.mal.domain.MalAnimeSummary
import com.greenzean.animekmp.shared.mal.domain.MalApiError
import com.greenzean.animekmp.shared.mal.domain.MalError
import com.greenzean.animekmp.shared.mal.domain.MalSearchPage
import com.greenzean.animekmp.shared.mal.domain.MalThrottle
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private class MutableClock(
    var current: Instant,
) : Clock {
    override fun now(): Instant = current
}

private class FakeMalAnimeApi : MalAnimeApi {
    var searchCalls = 0
    var detailsCalls = 0
    var searchResult: Result<MalSearchPage, MalApiError> = Result.Success(MalSearchPage(emptyList(), false))
    var detailsResult: Result<MalAnimeDetails, MalApiError> = Result.Error(MalApiError.UNKNOWN)

    override suspend fun search(
        query: String,
        limit: Int,
        offset: Int,
    ): Result<MalSearchPage, MalApiError> {
        searchCalls++
        return searchResult
    }

    override suspend fun details(malId: Int): Result<MalAnimeDetails, MalApiError> {
        detailsCalls++
        return detailsResult
    }
}

class MalAnimeRepositoryTest {
    private val driver = createTestDriver()
    private val clock = MutableClock(Instant.fromEpochMilliseconds(1_000_000_000))
    private val api = FakeMalAnimeApi()
    private val cache = SqlDelightMalCache(AnimeDatabase(driver), UnconfinedTestDispatcher())
    private val sink = RecordingLogSink()
    private val repository =
        MalAnimeRepository(api, cache, MalThrottle(clock), clock, logger = SinkLogger(listOf(sink)))

    private val bebop = MalAnimeSummary(1, "Cowboy Bebop", null, 8.7, "tv", 26, "finished_airing")
    private val page = MalSearchPage(listOf(bebop), hasMore = false)
    private val details = MalAnimeDetails(bebop, "Space cowboys.", listOf("Action"), "1998-04-03", 40, emptyList())

    @AfterTest
    fun tearDown() = driver.close()

    @Test
    fun searchWithinTheExpiryComesFromTheCache() =
        runTest {
            api.searchResult = Result.Success(page)
            repository.search("Bebop")
            clock.current += 59.minutes

            val result = repository.search("  bebop ")

            assertEquals(Result.Success(page), result)
            assertEquals(1, api.searchCalls)
        }

    @Test
    fun searchAfterTheExpiryFetchesAgain() =
        runTest {
            api.searchResult = Result.Success(page)
            repository.search("bebop")
            clock.current += 61.minutes

            repository.search("bebop")

            assertEquals(2, api.searchCalls)
        }

    @Test
    fun searchPagesAreCachedSeparately() =
        runTest {
            api.searchResult = Result.Success(page)
            repository.search("bebop", 0)

            repository.search("bebop", 20)

            assertEquals(2, api.searchCalls)
        }

    @Test
    fun blankSearchMakesNoCall() =
        runTest {
            val result = repository.search("   ")

            assertEquals(Result.Success(MalSearchPage(emptyList(), false)), result)
            assertEquals(0, api.searchCalls)
        }

    @Test
    fun detailsStayFreshForADay() =
        runTest {
            api.detailsResult = Result.Success(details)
            repository.details(1)
            clock.current += 23.hours

            assertEquals(Result.Success(details), repository.details(1))
            assertEquals(1, api.detailsCalls)

            clock.current += 2.hours
            repository.details(1)
            assertEquals(2, api.detailsCalls)
        }

    @Test
    fun offlineWithAnExpiredEntryReturnsTheStaleCopy() =
        runTest {
            api.detailsResult = Result.Success(details)
            repository.details(1)
            clock.current += 2.days
            api.detailsResult = Result.Error(MalApiError.NO_CONNECTION)

            assertEquals(Result.Success(details), repository.details(1))
        }

    @Test
    fun offlineWithoutACachedEntryIsNoConnection() =
        runTest {
            api.detailsResult = Result.Error(MalApiError.NO_CONNECTION)

            assertEquals(Result.Error(MalError.NoConnection), repository.details(1))
        }

    @Test
    fun serverErrorWithAnExpiredEntryIsNotHiddenBehindTheCache() =
        runTest {
            api.detailsResult = Result.Success(details)
            repository.details(1)
            clock.current += 2.days
            api.detailsResult = Result.Error(MalApiError.SERVER)

            assertEquals(Result.Error(MalError.Server), repository.details(1))
        }

    @Test
    fun throttleStopsFurtherCallsUntilTheBackoffEnds() =
        runTest {
            api.searchResult = Result.Error(MalApiError.THROTTLED)

            val first = repository.search("one")
            val second = repository.search("two")

            assertEquals(Result.Error(MalError.Throttled(clock.current + 30.seconds)), first)
            assertEquals(first, second)
            assertEquals(1, api.searchCalls)
        }

    @Test
    fun forbiddenBlocksDetailsToo() =
        runTest {
            api.searchResult = Result.Error(MalApiError.FORBIDDEN)
            repository.search("one")

            val result = repository.details(1)

            assertEquals(Result.Error(MalError.Blocked(clock.current + 1.hours)), result)
            assertEquals(0, api.detailsCalls)
        }

    @Test
    fun entriesOlderThanTheStaleLimitAreDeletedOnTheNextSave() =
        runTest {
            api.detailsResult = Result.Success(details)
            repository.details(1)
            clock.current += 31.days
            api.detailsResult = Result.Error(MalApiError.NO_CONNECTION)
            api.searchResult = Result.Success(page)
            repository.search("bebop")

            assertEquals(Result.Error(MalError.NoConnection), repository.details(1))
        }

    @Test
    fun clearCacheDropsEverything() =
        runTest {
            api.detailsResult = Result.Success(details)
            repository.details(1)
            repository.clearCache()
            api.detailsResult = Result.Error(MalApiError.NO_CONNECTION)

            assertEquals(Result.Error(MalError.NoConnection), repository.details(1))
        }

    @Test
    fun searchLogsMissThenHitWithoutTheQuery() =
        runTest {
            api.searchResult = Result.Success(page)

            repository.search("SENTINEL-QUERY")
            repository.search("SENTINEL-QUERY")

            assertEquals(listOf("search miss", "search hit"), sink.messages("MalCache"))
            sink.assertNoneContains("SENTINEL-QUERY", "sentinel-query", "Cowboy Bebop")
        }

    @Test
    fun detailsLogMissThenHit() =
        runTest {
            api.detailsResult = Result.Success(details)

            repository.details(1)
            repository.details(1)

            assertEquals(listOf("details miss", "details hit"), sink.messages("MalCache"))
        }

    @Test
    fun anOfflineRefreshOfStaleDetailsLogsTheFallback() =
        runTest {
            api.detailsResult = Result.Success(details)
            repository.details(1)
            clock.current += 2.days
            api.detailsResult = Result.Error(MalApiError.NO_CONNECTION)

            repository.details(1)

            assertEquals(listOf("details miss", "details stale-fallback"), sink.messages("MalCache"))
        }

    private fun traceRepository(traceSink: RecordingLogSink) =
        MalAnimeRepository(api, cache, MalThrottle(clock), clock, logger = SinkLogger(listOf(traceSink)))

    @Test
    fun theApiStreamShowsMissHitAndExpiredForASearch() =
        runTest {
            val traceSink = RecordingLogSink(minLevel = LogLevel.Trace)
            val traced = traceRepository(traceSink)
            api.searchResult = Result.Success(page)

            traced.search("Bebop")
            traced.search("bebop")
            clock.current += 2.hours
            traced.search("bebop")

            val lines = traceSink.messages("API")
            assertEquals(3, lines.size, lines.toString())
            assertTrue(lines[0].startsWith("API  CACHE miss  search 'bebop' page 0"), lines[0])
            assertTrue(lines[1].startsWith("API  CACHE hit  search 'bebop' page 0  (age "), lines[1])
            assertTrue("no request sent" in lines[1], lines[1])
            assertTrue(lines[2].startsWith("API  CACHE expired  search 'bebop' page 0  (age 2h 0m)"), lines[2])
            assertEquals(2, api.searchCalls)
        }

    @Test
    fun theApiStreamShowsTheFallbackForDetails() =
        runTest {
            val traceSink = RecordingLogSink(minLevel = LogLevel.Trace)
            val traced = traceRepository(traceSink)
            api.detailsResult = Result.Success(details)
            traced.details(1)
            clock.current += 2.days
            api.detailsResult = Result.Error(MalApiError.NO_CONNECTION)

            traced.details(1)

            val lines = traceSink.messages("API")
            val last = lines.last()
            assertTrue(last.startsWith("API  CACHE stale-fallback  details 1  (no connection, age "), lines.toString())
        }

    @Test
    fun aDebugSinkSeesNoApiStreamLines() =
        runTest {
            api.searchResult = Result.Success(page)

            repository.search("bebop")
            repository.search("bebop")

            assertTrue(sink.entries.none { it.tag == "API" })
        }
}
