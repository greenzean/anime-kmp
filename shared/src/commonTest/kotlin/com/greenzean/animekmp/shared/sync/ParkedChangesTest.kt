package com.greenzean.animekmp.shared.sync

import com.greenzean.animekmp.shared.core.data.createTestDriver
import com.greenzean.animekmp.shared.core.data.db.AnimeDatabase
import com.greenzean.animekmp.shared.library.domain.LOCAL_USER_ID
import com.greenzean.animekmp.shared.library.domain.WatchStatus
import com.greenzean.animekmp.shared.sync.data.SqlDelightSyncLocalDataSource
import com.greenzean.animekmp.shared.sync.domain.SyncChange
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class ParkedChangesTest {
    private val driver = createTestDriver()
    private val db = AnimeDatabase(driver)
    private val local = SqlDelightSyncLocalDataSource(db, UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = driver.close()

    private fun change(
        id: String,
        updatedAt: Long,
        status: WatchStatus = WatchStatus.COMPLETED,
        deletedAt: Long? = null,
    ) = SyncChange(
        id = id,
        malId = ANIME_ID,
        status = status,
        updatedAt = Instant.fromEpochMilliseconds(updatedAt),
        deletedAt = deletedAt?.let(Instant::fromEpochMilliseconds),
    )

    private fun addAnime() = db.animeQueries.insertOrIgnore(ANIME_ID.toLong(), "Cowboy Bebop")

    private fun row() = db.listEntryQueries.selectByUserAndMalId(LOCAL_USER_ID, ANIME_ID.toLong()).executeAsOneOrNull()

    private fun parkedCount() =
        db.parkedChangeQueries
            .selectAll()
            .executeAsList()
            .size

    @Test
    fun unknownAnimeIsParkedAndCursorStillAdvances() =
        runTest {
            val applied = local.applyPage(listOf(change("e1", 1_000)), "c1")

            assertEquals(0, applied)
            assertEquals(1, parkedCount())
            assertNull(row())
            assertEquals("c1", local.getCursor())
        }

    @Test
    fun retryAppliesParkedChangeOnceAnimeArrives() =
        runTest {
            local.applyPage(listOf(change("e1", 1_000)), "c1")
            addAnime()

            assertEquals(1, local.retryParked())

            assertEquals("COMPLETED", row()!!.status)
            assertEquals("SYNCED", row()!!.sync_state)
            assertEquals(0, parkedCount())
        }

    @Test
    fun retryDoesNothingWhileAnimeIsStillMissing() =
        runTest {
            local.applyPage(listOf(change("e1", 1_000)), "c1")

            assertEquals(0, local.retryParked())
            assertEquals(1, parkedCount())
        }

    @Test
    fun newestParkedChangeWins() =
        runTest {
            local.applyPage(listOf(change("e1", 2_000, WatchStatus.DROPPED)), "c1")
            local.applyPage(listOf(change("e1", 1_000, WatchStatus.WATCHING)), "c2")
            addAnime()

            local.retryParked()

            assertEquals("DROPPED", row()!!.status)
        }

    @Test
    fun parkedTombstoneAppliesAsTombstone() =
        runTest {
            local.applyPage(listOf(change("e1", 1_000, deletedAt = 1_000)), "c1")
            addAnime()

            local.retryParked()

            assertEquals(1_000L, row()!!.deleted_at)
        }

    @Test
    fun pullAfterAnimeArrivedSupersedesOlderParkedChange() =
        runTest {
            local.applyPage(listOf(change("e1", 1_000, WatchStatus.WATCHING)), "c1")
            addAnime()
            local.applyPage(listOf(change("e1", 2_000, WatchStatus.DROPPED)), "c2")

            local.retryParked()

            assertEquals("DROPPED", row()!!.status)
            assertEquals(0, parkedCount())
        }

    private companion object {
        const val ANIME_ID = 42
    }
}
