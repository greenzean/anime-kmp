package com.greenzean.animekmp.shared.auth

import com.greenzean.animekmp.shared.auth.data.InMemorySessionStore
import com.greenzean.animekmp.shared.auth.domain.LibraryChoice
import com.greenzean.animekmp.shared.auth.domain.MergeLibraryUseCase
import com.greenzean.animekmp.shared.auth.domain.MergeSummary
import com.greenzean.animekmp.shared.auth.domain.SessionManager
import com.greenzean.animekmp.shared.auth.domain.SessionRefresher
import com.greenzean.animekmp.shared.auth.domain.SignOutUseCase
import com.greenzean.animekmp.shared.core.data.DevAnimeSeed
import com.greenzean.animekmp.shared.core.data.createTestDriver
import com.greenzean.animekmp.shared.core.data.db.AnimeDatabase
import com.greenzean.animekmp.shared.core.domain.DataError
import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.library.data.SqlDelightLibraryDataSource
import com.greenzean.animekmp.shared.library.domain.WatchStatus
import com.greenzean.animekmp.shared.sync.FakeServerSyncApi
import com.greenzean.animekmp.shared.sync.data.SqlDelightAccountLibraryDataSource
import com.greenzean.animekmp.shared.sync.data.SqlDelightSyncLocalDataSource
import com.greenzean.animekmp.shared.sync.domain.SyncEngine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Instant

private const val BEBOP = 1
private const val FMAB = 5114
private const val STEINS_GATE = 9253
private const val AOT = 16498

private class TickClock : Clock {
    var millis = 1_000L

    override fun now(): Instant = Instant.fromEpochMilliseconds(millis)
}

private class Device(
    server: FakeServerSyncApi,
) {
    val driver = createTestDriver()
    val db = AnimeDatabase(driver).also(DevAnimeSeed::apply)
    val clock = TickClock()
    private val dispatcher = UnconfinedTestDispatcher()
    val library = SqlDelightLibraryDataSource(db, clock, dispatcher)
    val local = SqlDelightSyncLocalDataSource(db, dispatcher)
    val accountLibrary = SqlDelightAccountLibraryDataSource(db, dispatcher)
    val store = InMemorySessionStore()
    val scheduler = FakeSyncScheduler()
    var engine = SyncEngine(server, local)

    val merge get() = MergeLibraryUseCase(engine, accountLibrary, scheduler)
    val signOut get() = SignOutUseCase(store, engine, accountLibrary)

    fun tombstones() =
        db.listEntryQueries
            .selectByMalId(BEBOP.toLong())
            .executeAsOneOrNull()
            ?.deleted_at

    fun connectTo(server: FakeServerSyncApi) {
        engine = SyncEngine(server, local)
    }

    suspend fun set(
        malId: Int,
        status: WatchStatus,
        at: Long,
    ) {
        clock.millis = at
        library.setStatus(malId, status)
    }

    fun statuses(): Map<Int, String> =
        db.listEntryQueries
            .selectActiveWithAnime()
            .executeAsList()
            .associate { it.mal_id.toInt() to it.status }

    fun rowCount() =
        db.listEntryQueries
            .countActive()
            .executeAsOne()
            .toInt()

    fun pending() =
        db.listEntryQueries
            .countPending()
            .executeAsOne()
            .toInt()
}

class MergeAndSignOutTest {
    private val server = FakeServerSyncApi()
    private val device = Device(server)
    private val other = Device(server)

    @AfterTest
    fun tearDown() {
        device.driver.close()
        other.driver.close()
    }

    @Test
    fun firstLoginPushesTheDeviceLibraryToTheAccount() =
        runTest {
            device.set(BEBOP, WatchStatus.WATCHING, at = 10)
            device.set(STEINS_GATE, WatchStatus.COMPLETED, at = 20)

            val result = device.merge()

            assertEquals(Result.Success(MergeSummary(sent = 2, rejected = 0, total = 2)), result)
            assertEquals(0, device.pending())
            assertEquals(2, server.changes.size)
        }

    @Test
    fun entriesOnTwoDevicesMergeByLastWriteWins() =
        runTest {
            device.set(BEBOP, WatchStatus.WATCHING, at = 10)
            device.set(STEINS_GATE, WatchStatus.COMPLETED, at = 50)
            device.set(FMAB, WatchStatus.PLAN_TO_WATCH, at = 15)
            other.set(BEBOP, WatchStatus.DROPPED, at = 30)
            other.set(STEINS_GATE, WatchStatus.ON_HOLD, at = 40)
            other.set(AOT, WatchStatus.WATCHING, at = 45)

            device.merge()
            val second = other.merge()
            device.merge()

            val expected =
                mapOf(
                    BEBOP to "DROPPED",
                    STEINS_GATE to "COMPLETED",
                    FMAB to "PLAN_TO_WATCH",
                    AOT to "WATCHING",
                )
            assertEquals(expected, device.statuses())
            assertEquals(expected, other.statuses())
            assertEquals(Result.Success(MergeSummary(sent = 3, rejected = 0, total = 4)), second)
            assertEquals(0, device.pending())
            assertEquals(0, other.pending())
        }

    @Test
    fun anAccountEntryComesToTheDeviceOnFirstLogin() =
        runTest {
            other.set(AOT, WatchStatus.COMPLETED, at = 10)
            other.merge()

            val result = device.merge()

            assertEquals(Result.Success(MergeSummary(sent = 0, rejected = 0, total = 1)), result)
            assertEquals(mapOf(AOT to "COMPLETED"), device.statuses())
        }

    @Test
    fun aMergeWithoutConnectionKeepsTheRowsPendingAndAsksForBackgroundSync() =
        runTest {
            device.set(BEBOP, WatchStatus.WATCHING, at = 10)
            server.offline = true

            val result = device.merge()

            assertEquals(Result.Error(DataError.Network.NO_INTERNET), result)
            assertEquals(1, device.pending())
            assertEquals(1, device.scheduler.requests)

            server.offline = false
            assertEquals(Result.Success(MergeSummary(sent = 1, rejected = 0, total = 1)), device.merge())
        }

    @Test
    fun keepLeavesTheLibraryPendingAndSignsOut() =
        runTest {
            device.store.save(SESSION)
            device.set(BEBOP, WatchStatus.WATCHING, at = 10)
            device.merge()
            assertEquals(0, device.pending())

            device.signOut(LibraryChoice.KEEP)

            assertNull(device.store.get())
            assertEquals(mapOf(BEBOP to "WATCHING"), device.statuses())
            assertEquals(1, device.pending())
            assertNull(device.local.getCursor())
        }

    @Test
    fun deleteEmptiesTheLibraryAndSignsOut() =
        runTest {
            device.store.save(SESSION)
            device.set(BEBOP, WatchStatus.WATCHING, at = 10)
            other.set(AOT, WatchStatus.COMPLETED, at = 20)
            other.merge()
            device.merge()
            assertNotNull(device.local.getCursor())

            device.signOut(LibraryChoice.DELETE)

            assertNull(device.store.get())
            assertEquals(emptyMap(), device.statuses())
            assertEquals(
                0,
                device.db.listEntryQueries
                    .selectPending()
                    .executeAsList()
                    .size,
            )
            assertEquals(
                0,
                device.db.parkedChangeQueries
                    .selectAll()
                    .executeAsList()
                    .size,
            )
            assertNull(device.local.getCursor())
            assertEquals(
                4,
                device.db.animeQueries
                    .selectAll()
                    .executeAsList()
                    .size,
            )
        }

    @Test
    fun prepareReportsChangesThatCouldNotBePushed() =
        runTest {
            device.set(BEBOP, WatchStatus.WATCHING, at = 10)
            device.set(AOT, WatchStatus.COMPLETED, at = 20)
            server.offline = true

            assertEquals(2, device.signOut.prepare())

            server.offline = false
            assertEquals(0, device.signOut.prepare())
        }

    @Test
    fun aKeptLibraryMergesIntoADifferentAccount() =
        runTest {
            device.store.save(SESSION)
            device.set(BEBOP, WatchStatus.WATCHING, at = 10)
            device.set(STEINS_GATE, WatchStatus.COMPLETED, at = 20)
            device.merge()
            device.signOut(LibraryChoice.KEEP)

            val otherAccount = FakeServerSyncApi()
            val third = Device(otherAccount)
            third.set(AOT, WatchStatus.DROPPED, at = 5)
            third.merge()
            device.connectTo(otherAccount)

            val result = device.merge()

            assertEquals(Result.Success(MergeSummary(sent = 2, rejected = 0, total = 3)), result)
            assertEquals(3, otherAccount.changes.size)
            assertEquals(0, device.pending())
            assertEquals(mapOf(BEBOP to "WATCHING", STEINS_GATE to "COMPLETED", AOT to "DROPPED"), device.statuses())
            third.driver.close()
        }

    @Test
    fun keepDropsSyncedDeletionsSoTheyCannotDeleteFromAnotherAccount() =
        runTest {
            device.set(BEBOP, WatchStatus.WATCHING, at = 10)
            device.merge()
            device.clock.millis = 100
            device.library.remove(BEBOP)
            device.merge()
            device.signOut(LibraryChoice.KEEP)
            assertNull(device.tombstones())

            val otherAccount = FakeServerSyncApi()
            val third = Device(otherAccount)
            third.set(BEBOP, WatchStatus.COMPLETED, at = 50)
            third.merge()
            device.connectTo(otherAccount)

            device.merge()

            assertEquals(mapOf(BEBOP to "COMPLETED"), device.statuses())
            assertNull(otherAccount.change(BEBOP)?.deletedAt)
            third.driver.close()
        }

    @Test
    fun keepStillPushesDeletionsThatWereNeverSynced() =
        runTest {
            device.set(BEBOP, WatchStatus.WATCHING, at = 10)
            device.merge()
            server.offline = true
            device.clock.millis = 100
            device.library.remove(BEBOP)
            device.signOut(LibraryChoice.KEEP)
            server.offline = false

            device.merge()

            assertNotNull(server.change(BEBOP)?.deletedAt)
        }

    @Test
    fun aMergeCancelledMidRunAsksForBackgroundSync() =
        runTest {
            device.set(BEBOP, WatchStatus.WATCHING, at = 10)
            val gate = CompletableDeferred<Unit>()
            server.pullGate = gate
            val job = launch(UnconfinedTestDispatcher(testScheduler)) { device.merge() }
            assertEquals(0, device.scheduler.requests)

            job.cancelAndJoin()

            assertEquals(1, device.scheduler.requests)
            assertEquals(0, device.pending())
        }

    @Test
    fun aSessionEndedByTheServerDetachesTheLibraryFromTheAccount() =
        runTest {
            device.store.save(SESSION.copy(accessTokenExpiresAt = Instant.fromEpochMilliseconds(0)))
            device.set(BEBOP, WatchStatus.WATCHING, at = 10)
            device.merge()
            assertNotNull(device.local.getCursor())
            val rejected = SessionRefresher { Result.Error(DataError.Network.UNAUTHORIZED) }
            val manager =
                SessionManager(device.store, rejected, onSessionEnded = device.accountLibrary::detachFromAccount)

            assertEquals(Result.Error(DataError.Network.UNAUTHORIZED), manager.accessToken())

            assertNull(device.store.get())
            assertNull(device.local.getCursor())
            assertEquals(1, device.pending())
            val otherAccount = FakeServerSyncApi()
            val third = Device(otherAccount)
            third.set(AOT, WatchStatus.DROPPED, at = 5)
            third.merge()
            device.connectTo(otherAccount)
            device.merge()
            assertEquals(mapOf(BEBOP to "WATCHING", AOT to "DROPPED"), device.statuses())
            third.driver.close()
        }

    @Test
    fun theSummaryCountsEntriesTheServerRejected() =
        runTest {
            device.set(BEBOP, WatchStatus.WATCHING, at = 10)
            device.set(AOT, WatchStatus.COMPLETED, at = 20)
            server.rejectedMalIds += AOT

            val result = device.merge()

            assertEquals(Result.Success(MergeSummary(sent = 1, rejected = 1, total = 2)), result)
            assertEquals(1, device.pending())
        }

    @Test
    fun signOutWaitsForASyncAlreadyRunning() =
        runTest {
            device.store.save(SESSION)
            other.set(AOT, WatchStatus.DROPPED, at = 5)
            other.merge()
            val gate = CompletableDeferred<Unit>()
            server.pullGate = gate
            val merging = launch { device.merge() }
            runCurrent()
            val signingOut = launch { device.signOut(LibraryChoice.DELETE) }
            runCurrent()

            assertNotNull(device.store.get())

            gate.complete(Unit)
            merging.join()
            signingOut.join()

            assertNull(device.store.get())
            assertEquals(emptyMap(), device.statuses())
        }
}
