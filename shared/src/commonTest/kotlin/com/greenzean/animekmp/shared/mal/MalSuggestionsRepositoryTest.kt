package com.greenzean.animekmp.shared.mal

import com.greenzean.animekmp.shared.core.data.createTestDriver
import com.greenzean.animekmp.shared.core.data.db.AnimeDatabase
import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.mal.data.SqlDelightMalCache
import com.greenzean.animekmp.shared.mal.domain.MAL_SUGGESTIONS_MAX
import com.greenzean.animekmp.shared.mal.domain.MAL_SUGGESTIONS_PAGE_SIZE
import com.greenzean.animekmp.shared.mal.domain.MAL_SUGGESTIONS_TTL
import com.greenzean.animekmp.shared.mal.domain.MalAnimeSummary
import com.greenzean.animekmp.shared.mal.domain.MalApiError
import com.greenzean.animekmp.shared.mal.domain.MalError
import com.greenzean.animekmp.shared.mal.domain.MalSearchPage
import com.greenzean.animekmp.shared.mal.domain.MalSuggestionsApi
import com.greenzean.animekmp.shared.mal.domain.MalSuggestionsRepository
import com.greenzean.animekmp.shared.mal.domain.MalThrottle
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private class SuggestionsClock(
    var current: Instant,
) : Clock {
    override fun now(): Instant = current
}

private class FakeSuggestionsApi : MalSuggestionsApi {
    val calls = mutableListOf<Pair<Int, Int>>()
    var result: Result<MalSearchPage, MalApiError> = Result.Success(MalSearchPage(emptyList(), false))

    override suspend fun suggestions(
        limit: Int,
        offset: Int,
    ): Result<MalSearchPage, MalApiError> {
        calls += limit to offset
        return result
    }
}

class MalSuggestionsRepositoryTest {
    private val driver = createTestDriver()
    private val clock = SuggestionsClock(Instant.fromEpochMilliseconds(1_000_000_000))
    private val api = FakeSuggestionsApi()
    private val cache = SqlDelightMalCache(AnimeDatabase(driver), UnconfinedTestDispatcher())
    private val throttle = MalThrottle(clock)
    private val repository = MalSuggestionsRepository(api, cache, throttle, clock)

    private val bebop = MalAnimeSummary(1, "Cowboy Bebop", null, 8.7, "tv", 26, "finished_airing")
    private val page = MalSearchPage(listOf(bebop), hasMore = true)

    @AfterTest
    fun tearDown() = driver.close()

    @Test
    fun constantsMatchTheAgreedValues() {
        assertEquals(1.days, MAL_SUGGESTIONS_TTL)
        assertEquals(20, MAL_SUGGESTIONS_PAGE_SIZE)
        assertEquals(100, MAL_SUGGESTIONS_MAX)
    }

    @Test
    fun fetchesWithThePageSizeAndTheOffset() =
        runTest {
            api.result = Result.Success(page)

            repository.suggestions(40)

            assertEquals(listOf(20 to 40), api.calls)
        }

    @Test
    fun withinADayTheCacheAnswers() =
        runTest {
            api.result = Result.Success(page)
            repository.suggestions(0)
            clock.current += 23.hours

            val result = repository.suggestions(0)

            assertEquals(Result.Success(page), result)
            assertEquals(1, api.calls.size)
        }

    @Test
    fun afterADayItFetchesAgain() =
        runTest {
            api.result = Result.Success(page)
            repository.suggestions(0)
            clock.current += 25.hours

            repository.suggestions(0)

            assertEquals(2, api.calls.size)
        }

    @Test
    fun pagesAreCachedSeparately() =
        runTest {
            api.result = Result.Success(page)
            repository.suggestions(0)

            repository.suggestions(20)

            assertEquals(listOf(20 to 0, 20 to 20), api.calls)
        }

    @Test
    fun offlineWithAStaleCopyReturnsIt() =
        runTest {
            api.result = Result.Success(page)
            repository.suggestions(0)
            clock.current += 2.days
            api.result = Result.Error(MalApiError.NO_CONNECTION)

            assertEquals(Result.Success(page), repository.suggestions(0))
        }

    @Test
    fun offlineWithoutACopyIsNoConnection() =
        runTest {
            api.result = Result.Error(MalApiError.NO_CONNECTION)

            assertEquals(Result.Error(MalError.NoConnection), repository.suggestions(0))
        }

    @Test
    fun serverErrorIsNotHiddenBehindTheStaleCopy() =
        runTest {
            api.result = Result.Success(page)
            repository.suggestions(0)
            clock.current += 2.days
            api.result = Result.Error(MalApiError.SERVER)

            assertEquals(Result.Error(MalError.Server), repository.suggestions(0))
        }

    @Test
    fun hasMoreStaysTrueBeforeTheLastPage() =
        runTest {
            api.result = Result.Success(page)

            assertEquals(true, (repository.suggestions(60) as Result.Success).data.hasMore)
        }

    @Test
    fun hasMoreIsFalseOnceTheCapIsReached() =
        runTest {
            api.result = Result.Success(page)

            val result = repository.suggestions(MAL_SUGGESTIONS_MAX - MAL_SUGGESTIONS_PAGE_SIZE)

            assertEquals(false, (result as Result.Success).data.hasMore)
            assertEquals(listOf(bebop), result.data.items)
        }

    @Test
    fun hasMoreIsFalseFromTheCacheAtTheCapToo() =
        runTest {
            api.result = Result.Success(page)
            repository.suggestions(80)

            val result = repository.suggestions(80)

            assertEquals(false, (result as Result.Success).data.hasMore)
        }

    @Test
    fun throttleStopsFurtherCallsUntilTheBackoffEnds() =
        runTest {
            api.result = Result.Error(MalApiError.THROTTLED)

            val first = repository.suggestions(0)
            val second = repository.suggestions(20)

            assertEquals(Result.Error(MalError.Throttled(clock.current + 30.seconds)), first)
            assertEquals(first, second)
            assertEquals(1, api.calls.size)
        }

    @Test
    fun theThrottleIsSharedWithOtherMalCalls() =
        runTest {
            throttle.run<Unit> { Result.Error(MalApiError.FORBIDDEN) }

            val result = repository.suggestions(0)

            assertEquals(Result.Error(MalError.Blocked(clock.current + 1.hours)), result)
            assertEquals(0, api.calls.size)
        }

    @Test
    fun clearingTheCacheWipesTheSuggestions() =
        runTest {
            api.result = Result.Success(page)
            repository.suggestions(0)

            cache.clear()
            api.result = Result.Error(MalApiError.NO_CONNECTION)

            assertEquals(Result.Error(MalError.NoConnection), repository.suggestions(0))
        }
}
