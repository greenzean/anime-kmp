package com.greenzean.animekmp.shared.sync

import com.greenzean.animekmp.shared.core.data.TestAnimeCatalog
import com.greenzean.animekmp.shared.core.data.createTestDriver
import com.greenzean.animekmp.shared.core.data.db.AnimeDatabase
import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.library.domain.WatchStatus
import com.greenzean.animekmp.shared.mal.MalOutboxHarness
import com.greenzean.animekmp.shared.mal.data.SqlDelightMalOutboxDataSource
import com.greenzean.animekmp.shared.mal.domain.MalChange
import com.greenzean.animekmp.shared.mal.domain.QueueMalChangeUseCase
import com.greenzean.animekmp.shared.sync.data.SqlDelightSyncLocalDataSource
import com.greenzean.animekmp.shared.sync.domain.ChangesPage
import com.greenzean.animekmp.shared.sync.domain.SyncChange
import com.greenzean.animekmp.shared.sync.domain.SyncEngine
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Instant

class PulledChangesNeverQueuedToMalTest {
    private val driver = createTestDriver()
    private val db = AnimeDatabase(driver).also(TestAnimeCatalog::apply)
    private val dispatcher = UnconfinedTestDispatcher()
    private val api = FakeSyncApi()
    private val engine = SyncEngine(api, SqlDelightSyncLocalDataSource(db, dispatcher))
    private val mal = MalOutboxHarness(loggedIn = true)
    private val outbox = SqlDelightMalOutboxDataSource(db, dispatcher)
    private val queue = QueueMalChangeUseCase(outbox, mal.auth, mal.scheduler)

    @AfterTest
    fun tearDown() = driver.close()

    private fun pending(): Long = db.malOutboxQueries.countPending().executeAsOne()

    private fun change(
        status: WatchStatus,
        progress: Int,
        at: Long,
    ) = SyncChange("e1", 1, status, progress, 9, Instant.fromEpochMilliseconds(at), null)

    @Test
    fun changesPulledFromOurServerAreNotQueuedEvenWithMalConnected() =
        runTest {
            api.pages += Result.Success(ChangesPage("1", false, listOf(change(WatchStatus.WATCHING, 3, 1_000))))
            api.pages += Result.Success(ChangesPage("2", false, listOf(change(WatchStatus.COMPLETED, 26, 2_000))))

            engine.sync()
            engine.sync()

            val entry = assertNotNull(db.listEntryQueries.selectByMalId(1).executeAsOneOrNull())
            assertEquals(26, entry.progress.toInt())
            assertEquals(0L, pending())
            assertEquals(null, outbox.oldest())
            assertEquals(0, mal.scheduler.requests)
        }

    @Test
    fun aLocalEditWithMalConnectedIsQueuedInTheRealOutbox() =
        runTest {
            queue(MalChange.Upsert(1, WatchStatus.COMPLETED, 26, 9))

            assertEquals(1L, pending())
            assertEquals(MalChange.Upsert(1, WatchStatus.COMPLETED, 26, 9), outbox.oldest()?.change)
            assertEquals(1, mal.scheduler.requests)
        }

    @Test
    fun aLocalEditWithoutAMalConnectionIsNotQueued() =
        runTest {
            val loggedOut = MalOutboxHarness(loggedIn = false)

            QueueMalChangeUseCase(outbox, loggedOut.auth, loggedOut.scheduler)(MalChange.Remove(1))

            assertEquals(0L, pending())
            assertEquals(0, loggedOut.scheduler.requests)
        }
}
