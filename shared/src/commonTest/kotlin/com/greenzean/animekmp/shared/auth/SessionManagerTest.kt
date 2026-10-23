package com.greenzean.animekmp.shared.auth

import com.greenzean.animekmp.shared.auth.data.InMemorySessionStore
import com.greenzean.animekmp.shared.auth.domain.Session
import com.greenzean.animekmp.shared.auth.domain.SessionManager
import com.greenzean.animekmp.shared.auth.domain.SessionRefresher
import com.greenzean.animekmp.shared.core.domain.DataError
import com.greenzean.animekmp.shared.core.domain.Result
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val NOW = Instant.parse("2026-10-22T12:00:00Z")

private class FixedClock : Clock {
    override fun now(): Instant = NOW
}

private class FakeRefresher : SessionRefresher {
    val refreshed = mutableListOf<String>()
    var result: Result<Session, DataError.Network> = Result.Success(session("fresh", "r2"))

    override suspend fun refresh(refreshToken: String): Result<Session, DataError.Network> {
        refreshed += refreshToken
        delay(100)
        return result
    }
}

private fun session(
    access: String,
    refresh: String,
    expiresIn: kotlin.time.Duration = 15.minutes,
) = Session(access, NOW + expiresIn, refresh)

class SessionManagerTest {
    private val store = InMemorySessionStore()
    private val refresher = FakeRefresher()
    private var sessionsEnded = 0
    private val manager = SessionManager(store, refresher, FixedClock(), onSessionEnded = { sessionsEnded++ })

    @Test
    fun noSessionIsUnauthorizedWithoutARefresh() =
        runTest {
            assertEquals(Result.Error(DataError.Network.UNAUTHORIZED), manager.accessToken())
            assertEquals(emptyList(), refresher.refreshed)
        }

    @Test
    fun aFreshTokenIsReturnedAsIs() =
        runTest {
            store.save(session("a1", "r1"))
            assertEquals(Result.Success("a1"), manager.accessToken())
            assertEquals(emptyList(), refresher.refreshed)
        }

    @Test
    fun anExpiredTokenIsRefreshedAndTheNewSessionSaved() =
        runTest {
            store.save(session("a1", "r1", expiresIn = (-1).minutes))
            assertEquals(Result.Success("fresh"), manager.accessToken())
            assertEquals(listOf("r1"), refresher.refreshed)
            assertEquals(session("fresh", "r2"), store.get())
        }

    @Test
    fun aTokenAboutToExpireIsRefreshedEarly() =
        runTest {
            store.save(Session("a1", NOW + kotlin.time.Duration.parse("10s"), "r1"))
            assertEquals(Result.Success("fresh"), manager.accessToken())
        }

    @Test
    fun concurrentCallersShareOneRefresh() =
        runTest {
            store.save(session("a1", "r1", expiresIn = (-1).minutes))
            val tokens = (1..5).map { async { manager.accessToken() } }.awaitAll()
            assertEquals(List(5) { Result.Success("fresh") }, tokens)
            assertEquals(listOf("r1"), refresher.refreshed)
        }

    @Test
    fun aRejectedTokenIsRenewed() =
        runTest {
            store.save(session("a1", "r1"))
            assertEquals(Result.Success("fresh"), manager.renewAfterRejection("a1"))
            assertEquals(listOf("r1"), refresher.refreshed)
        }

    @Test
    fun aRejectedTokenAlreadyReplacedIsNotRenewedAgain() =
        runTest {
            store.save(session("a2", "r2"))
            assertEquals(Result.Success("a2"), manager.renewAfterRejection("a1"))
            assertEquals(emptyList(), refresher.refreshed)
        }

    @Test
    fun aRejectedRefreshTokenEndsTheSession() =
        runTest {
            store.save(session("a1", "r1", expiresIn = (-1).minutes))
            refresher.result = Result.Error(DataError.Network.UNAUTHORIZED)
            assertEquals(Result.Error(DataError.Network.UNAUTHORIZED), manager.accessToken())
            assertNull(store.get())
            assertEquals(1, sessionsEnded)
        }

    @Test
    fun aNetworkFailureKeepsTheSession() =
        runTest {
            val expired = session("a1", "r1", expiresIn = (-1).minutes)
            store.save(expired)
            refresher.result = Result.Error(DataError.Network.NO_INTERNET)
            assertEquals(Result.Error(DataError.Network.NO_INTERNET), manager.accessToken())
            assertEquals(expired, store.get())
            assertEquals(0, sessionsEnded)
        }
}
