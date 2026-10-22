package com.greenzean.animekmp.shared.sync

import com.greenzean.animekmp.shared.core.data.DevAnimeSeed
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
    private val db = AnimeDatabase(driver).also(DevAnimeSeed::apply)
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
    ) = SyncChange(
        id,
        malId,
        status,
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
            library.setStatus(1, WatchStatus.WATCHING)
            library.setStatus(9253, WatchStatus.DROPPED)

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
            library.setStatus(1, WatchStatus.WATCHING)
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
            library.setStatus(1, WatchStatus.WATCHING)
            val id = row(1)!!.id
            api.pushOutcomes[id] = PushResult(id, PushOutcome.IGNORED_STALE, id)

            engine.sync()

            assertEquals("SYNCED", row(1)!!.sync_state)
        }

    @Test
    fun adoptsServerEntryIdOnPush() =
        runTest {
            library.setStatus(1, WatchStatus.WATCHING)
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
            library.setStatus(1, WatchStatus.WATCHING)
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
            library.setStatus(1, WatchStatus.WATCHING)
            api.onPush = {
                db.listEntryQueries.updatePending("COMPLETED", row(1)!!.updated_at + 1, 1)
            }

            engine.sync()

            assertEquals("PENDING", row(1)!!.sync_state)
            assertEquals("COMPLETED", row(1)!!.status)
        }

    @Test
    fun pushFailureReturnsFailureKeepsPendingAndSkipsPull() =
        runTest {
            library.setStatus(1, WatchStatus.WATCHING)
            api.pushError = DataError.Network.NO_INTERNET

            val result = engine.sync()

            assertEquals(SyncResult.Failure(DataError.Network.NO_INTERNET), result)
            assertEquals("PENDING", row(1)!!.sync_state)
            assertEquals(listOf("push"), api.calls)
        }

    @Test
    fun pushesBeforePulling() =
        runTest {
            library.setStatus(1, WatchStatus.WATCHING)
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

            db.animeQueries.insertOrIgnore(99, "Late Arrival")
            api.pages.add(page("c1", false))
            val result = engine.sync()

            assertEquals("COMPLETED", row(99)!!.status)
            assertEquals(1, (result as SyncResult.Success).pulled)
        }

    @Test
    fun newerPendingRowIsNotOverwrittenByPull() =
        runTest {
            library.setStatus(1, WatchStatus.WATCHING)
            val pending = row(1)!!

            local.applyPage(listOf(pulled(pending.id, 1, pending.updated_at - 10_000)), "c1")

            assertEquals("WATCHING", row(1)!!.status)
            assertEquals("PENDING", row(1)!!.sync_state)
        }

    @Test
    fun pullOverwritesSyncedRowAndAppliesTombstones() =
        runTest {
            library.setStatus(1, WatchStatus.WATCHING)
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
            library.setStatus(1, WatchStatus.WATCHING)
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
            library.setStatus(1, WatchStatus.WATCHING)

            val results = listOf(async { engine.sync() }, async { engine.sync() }).awaitAll()

            results.forEach { assertIs<SyncResult.Success>(it) }
            assertEquals(1, api.pushed.flatten().size) // the second run found nothing pending
        }
}
