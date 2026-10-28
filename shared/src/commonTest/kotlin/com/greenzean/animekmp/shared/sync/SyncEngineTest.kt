package com.greenzean.animekmp.shared.sync

import com.greenzean.animekmp.shared.core.data.TestAnimeCatalog
import com.greenzean.animekmp.shared.core.data.createTestDriver
import com.greenzean.animekmp.shared.core.data.db.AnimeDatabase
import com.greenzean.animekmp.shared.core.domain.DataError
import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.library.data.SqlDelightLibraryDataSource
import com.greenzean.animekmp.shared.library.domain.WatchStatus
import com.greenzean.animekmp.shared.sync.data.SqlDelightSyncLocalDataSource
import com.greenzean.animekmp.shared.sync.domain.ChangesPage
import com.greenzean.animekmp.shared.sync.domain.PushOutcome
import com.greenzean.animekmp.shared.sync.domain.PushResult
import com.greenzean.animekmp.shared.sync.domain.SyncChange
import com.greenzean.animekmp.shared.sync.domain.SyncEngine
import com.greenzean.animekmp.shared.sync.domain.SyncResult
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant

class SyncEngineTest {
    private val driver = createTestDriver()
    private val db = AnimeDatabase(driver).also(TestAnimeCatalog::apply)
    private val dispatcher = UnconfinedTestDispatcher()
    private val library = SqlDelightLibraryDataSource(db, dispatcher = dispatcher)
    private val local = SqlDelightSyncLocalDataSource(db, dispatcher)
    private val api = FakeSyncApi()
    private val engine = SyncEngine(api, local)

    @AfterTest
    fun tearDown() = driver.close()

    private fun row(malId: Int) =
        db.listEntryQueries
            .selectByMalId(malId.toLong())
            .executeAsOneOrNull()

    private fun pulled(
        id: String,
        malId: Int,
        updatedAt: Long,
        status: WatchStatus = WatchStatus.COMPLETED,
        deletedAt: Long? = null,
        progress: Int = 0,
        score: Int? = null,
    ) = SyncChange(
        id,
        malId,
        status,
        progress,
        score,
        Instant.fromEpochMilliseconds(updatedAt),
        deletedAt?.let(Instant::fromEpochMilliseconds),
    )

    private fun page(
        next: String,
        hasMore: Boolean,
        vararg changes: SyncChange,
    ) = Result.Success(ChangesPage(next, hasMore, changes.toList()))

    @Test
    fun pushesPendingRowsAndMarksThemSynced() =
        runTest {
            library.saveEntry(1, WatchStatus.WATCHING, 0, null)
            library.saveEntry(9253, WatchStatus.DROPPED, 0, null)

            val result = engine.sync()

            assertEquals(2, assertIs<SyncResult.Success>(result).pushed)
            assertEquals(
                listOf(1, 9253),
                api.pushed
                    .flatten()
                    .map { it.malId }
                    .sorted(),
            )
            assertEquals("SYNCED", row(1)!!.sync_state)
            assertEquals("SYNCED", row(9253)!!.sync_state)
        }

    @Test
    fun pushesTombstones() =
        runTest {
            library.saveEntry(1, WatchStatus.WATCHING, 0, null)
            engine.sync()
            library.remove(1)

            engine.sync()

            val change = api.pushed.flatten().last()
            assertEquals(1, change.malId)
            assertIs<Instant>(change.deletedAt)
            assertEquals("SYNCED", row(1)!!.sync_state)
        }

    @Test
    fun ignoredStaleCountsAsSynced() =
        runTest {
            library.saveEntry(1, WatchStatus.WATCHING, 0, null)
            val id = row(1)!!.id
            api.pushOutcomes[id] = PushResult(id, PushOutcome.IGNORED_STALE, id)

            engine.sync()

            assertEquals("SYNCED", row(1)!!.sync_state)
        }

    @Test
    fun adoptsServerEntryIdOnPush() =
        runTest {
            library.saveEntry(1, WatchStatus.WATCHING, 0, null)
            val id = row(1)!!.id
            api.pushOutcomes[id] = PushResult(id, PushOutcome.APPLIED, "server-id")

            engine.sync()

            val adopted = row(1)!!
            assertEquals("server-id", adopted.id)
            assertEquals("WATCHING", adopted.status)
            assertEquals("SYNCED", adopted.sync_state)
        }

    @Test
    fun rejectedRowsStayPendingAreSurfacedAndNotRetriedInTheRun() =
        runTest {
            library.saveEntry(1, WatchStatus.WATCHING, 0, null)
            val id = row(1)!!.id
            api.pushOutcomes[id] = PushResult(id, PushOutcome.REJECTED_INVALID, id)

            val result = assertIs<SyncResult.Success>(engine.sync())

            assertEquals(listOf(id), result.rejected.map { it.id })
            assertEquals("PENDING", row(1)!!.sync_state)
            assertEquals(1, api.pushed.flatten().size)
        }

    @Test
    fun editedDuringPushStaysPending() =
        runTest {
            library.saveEntry(1, WatchStatus.WATCHING, 0, null)
            api.onPush = {
                db.listEntryQueries.updatePending("COMPLETED", 0, null, row(1)!!.updated_at + 1, 1)
            }

            engine.sync()

            assertEquals("PENDING", row(1)!!.sync_state)
            assertEquals("COMPLETED", row(1)!!.status)
        }

    @Test
    fun pushFailureReturnsFailureKeepsPendingAndSkipsPull() =
        runTest {
            library.saveEntry(1, WatchStatus.WATCHING, 0, null)
            api.pushError = DataError.Network.NO_INTERNET

            val result = engine.sync()

            assertEquals(SyncResult.Failure(DataError.Network.NO_INTERNET), result)
            assertEquals("PENDING", row(1)!!.sync_state)
            assertEquals(listOf("push"), api.calls)
        }

    @Test
    fun pushesBeforePulling() =
        runTest {
            library.saveEntry(1, WatchStatus.WATCHING, 0, null)
            engine.sync()
            assertEquals(listOf("push", "pull"), api.calls)
        }

    @Test
    fun pullsPagesUntilNoMoreAndStoresCursorPerPage() =
        runTest {
            api.pages.add(page("c1", true, pulled("a", 1, 1000)))
            api.pages.add(page("c2", false, pulled("b", 5114, 2000)))

            val result = assertIs<SyncResult.Success>(engine.sync())

            assertEquals(2, result.pulled)
            assertEquals(listOf(null, "c1"), api.pullCursors)
            assertEquals("c2", local.getCursor())
            assertEquals("SYNCED", row(1)!!.sync_state)
            assertEquals("a", row(1)!!.id)
        }

    @Test
    fun nextRunPullsFromTheStoredCursor() =
        runTest {
            api.pages.add(page("c1", false, pulled("a", 1, 1000)))
            engine.sync()
            engine.sync()
            assertEquals(listOf(null, "c1"), api.pullCursors)
        }

    @Test
    fun failedPageKeepsThePreviousCursor() =
        runTest {
            api.pages.add(page("c1", true, pulled("a", 1, 1000)))
            api.pages.add(Result.Error(DataError.Network.SERVER_ERROR))

            val result = engine.sync()

            assertEquals(SyncResult.Failure(DataError.Network.SERVER_ERROR), result)
            assertEquals("c1", local.getCursor())
        }

    @Test
    fun parkedChangeAppliesOnLaterSyncOnceAnimeIsInCatalog() =
        runTest {
            api.pages.add(page("c1", false, pulled("e99", 99, 1_000)))
            engine.sync()
            assertEquals(null, row(99))

            db.animeQueries.insertOrIgnore(99, "Late Arrival", null, null, null)
            api.pages.add(page("c1", false))
            val result = engine.sync()

            assertEquals("COMPLETED", row(99)!!.status)
            assertEquals(1, (result as SyncResult.Success).pulled)
        }

    @Test
    fun newerPendingRowIsNotOverwrittenByPull() =
        runTest {
            library.saveEntry(1, WatchStatus.WATCHING, 0, null)
            val pending = row(1)!!

            local.applyPage(listOf(pulled(pending.id, 1, pending.updated_at - 10_000)), "c1")

            assertEquals("WATCHING", row(1)!!.status)
            assertEquals("PENDING", row(1)!!.sync_state)
        }

    @Test
    fun pullOverwritesSyncedRowAndAppliesTombstones() =
        runTest {
            library.saveEntry(1, WatchStatus.WATCHING, 0, null)
            engine.sync()
            val id = row(1)!!.id
            val later = row(1)!!.updated_at + 5_000
            api.pages.add(page("c1", false, pulled(id, 1, later, WatchStatus.DROPPED, deletedAt = later)))

            engine.sync()

            assertEquals("DROPPED", row(1)!!.status)
            assertEquals(later, row(1)!!.deleted_at)
        }

    @Test
    fun pulledRowWithDifferentIdRewritesLocalId() =
        runTest {
            library.saveEntry(1, WatchStatus.WATCHING, 0, null)
            engine.sync()
            val later = row(1)!!.updated_at + 5_000
            api.pages.add(page("c1", false, pulled("server-id", 1, later, WatchStatus.COMPLETED)))

            engine.sync()

            assertEquals("server-id", row(1)!!.id)
            assertEquals("COMPLETED", row(1)!!.status)
        }

    @Test
    fun pulledRowForUnknownAnimeIsSkipped() =
        runTest {
            api.pages.add(page("c1", false, pulled("x", 999, 1000)))

            val result = assertIs<SyncResult.Success>(engine.sync())

            assertEquals(0, result.pulled)
            assertEquals("c1", local.getCursor())
        }

    @Test
    fun concurrentSyncCallsRunOneAfterAnother() =
        runTest {
            library.saveEntry(1, WatchStatus.WATCHING, 0, null)

            val results = listOf(async { engine.sync() }, async { engine.sync() }).awaitAll()

            results.forEach { assertIs<SyncResult.Success>(it) }
            assertEquals(1, api.pushed.flatten().size)
        }

    @Test
    fun pushesProgressAndScore() =
        runTest {
            library.saveEntry(1, WatchStatus.WATCHING, 7, 9)

            engine.sync()

            val change = api.pushed.flatten().single()
            assertEquals(7, change.progress)
            assertEquals(9, change.score)
        }

    @Test
    fun pushesAnEntryWithoutScore() =
        runTest {
            library.saveEntry(1, WatchStatus.WATCHING, 3, null)

            engine.sync()

            assertEquals(
                null,
                api.pushed
                    .flatten()
                    .single()
                    .score,
            )
        }

    @Test
    fun pulledChangeStoresProgressAndScore() =
        runTest {
            api.pages += page("1", false, pulled("e1", 1, 100, WatchStatus.WATCHING, progress = 12, score = 8))

            engine.sync()

            val pulledRow = row(1)!!
            assertEquals(12L, pulledRow.progress)
            assertEquals(8L, pulledRow.score)
        }

    @Test
    fun pulledChangeUpdatesProgressAndScoreOfAnExistingEntry() =
        runTest {
            library.saveEntry(1, WatchStatus.WATCHING, 1, null)
            engine.sync()
            val id = row(1)!!.id
            val later = row(1)!!.updated_at + 5_000
            api.pages += page("2", false, pulled(id, 1, later, WatchStatus.COMPLETED, progress = 26, score = 10))

            engine.sync()

            val updated = row(1)!!
            assertEquals(26L, updated.progress)
            assertEquals(10L, updated.score)
            assertEquals("COMPLETED", updated.status)
        }

    @Test
    fun progressAndScoreRoundTripThroughTheServer() =
        runTest {
            val server = FakeServerSyncApi()
            val pushing = SyncEngine(server, local)
            library.saveEntry(5114, WatchStatus.WATCHING, 20, 7)
            pushing.sync()
            assertEquals(20, server.change(5114)!!.progress)
            assertEquals(7, server.change(5114)!!.score)

            val otherDriver = createTestDriver()
            val otherDb = AnimeDatabase(otherDriver).also(TestAnimeCatalog::apply)
            val otherLocal = SqlDelightSyncLocalDataSource(otherDb, dispatcher)
            SyncEngine(server, otherLocal).sync()

            val received = otherDb.listEntryQueries.selectByMalId(5114L).executeAsOne()
            assertEquals(20L, received.progress)
            assertEquals(7L, received.score)
            otherDriver.close()
        }
}
