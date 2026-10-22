package com.greenzean.animekmp.shared.library.data

import app.cash.turbine.test
import com.greenzean.animekmp.shared.core.data.DevAnimeSeed
import com.greenzean.animekmp.shared.core.data.createTestDriver
import com.greenzean.animekmp.shared.core.data.db.AnimeDatabase
import com.greenzean.animekmp.shared.library.domain.SyncState
import com.greenzean.animekmp.shared.library.domain.WatchStatus
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SqlDelightLibraryDataSourceTest {
    private val driver = createTestDriver()
    private val db = AnimeDatabase(driver).also(DevAnimeSeed::apply)
    private val repository = SqlDelightLibraryDataSource(db, dispatcher = UnconfinedTestDispatcher())
    private val bebop = 1

    @AfterTest
    fun tearDown() = driver.close()

    @Test
    fun setStatusForUnknownAnimeFailsForeignKey() =
        runTest {
            assertFailsWith<Exception> { repository.setStatus(999, WatchStatus.WATCHING) }
        }

    @Test
    fun observeAnimeEmitsSeededRowsByTitle() =
        runTest {
            repository.observeAnime().test {
                assertEquals(
                    listOf("Cowboy Bebop", "Fullmetal Alchemist: Brotherhood", "Shingeki no Kyojin", "Steins;Gate"),
                    awaitItem().map { it.title },
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun setStatusAddsPendingEntry() =
        runTest {
            repository.observeLibrary().test {
                assertTrue(awaitItem().isEmpty())

                repository.setStatus(bebop, WatchStatus.WATCHING)

                val entry = awaitItem().single()
                assertEquals("Cowboy Bebop", entry.anime.title)
                assertEquals(WatchStatus.WATCHING, entry.status)
                assertEquals(SyncState.PENDING, entry.syncState)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun setStatusTwiceUpdatesTheSameEntry() =
        runTest {
            repository.setStatus(bebop, WatchStatus.WATCHING)
            val id =
                db.listEntryQueries
                    .selectPending()
                    .executeAsOne()
                    .id

            repository.setStatus(bebop, WatchStatus.COMPLETED)

            val row = db.listEntryQueries.selectPending().executeAsOne()
            assertEquals(id, row.id)
            assertEquals("COMPLETED", row.status)
        }

    @Test
    fun markSyncedClearsPendingUntilNextLocalWrite() =
        runTest {
            repository.setStatus(bebop, WatchStatus.WATCHING)
            val row = db.listEntryQueries.selectPending().executeAsOne()

            db.listEntryQueries.markSynced(row.id, row.updated_at)
            assertTrue(
                db.listEntryQueries
                    .selectPending()
                    .executeAsList()
                    .isEmpty(),
            )

            repository.setStatus(bebop, WatchStatus.DROPPED)
            assertEquals(
                1,
                db.listEntryQueries
                    .selectPending()
                    .executeAsList()
                    .size,
            )
        }

    @Test
    fun removeHidesEntryButKeepsPendingTombstone() =
        runTest {
            repository.setStatus(bebop, WatchStatus.WATCHING)

            repository.remove(bebop)

            repository.observeLibrary().test {
                assertTrue(awaitItem().isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
            val tombstone = db.listEntryQueries.selectPending().executeAsOne()
            assertTrue(tombstone.deleted_at != null)
        }

    @Test
    fun readdingAfterRemoveRevivesTheEntry() =
        runTest {
            repository.setStatus(bebop, WatchStatus.WATCHING)
            repository.remove(bebop)

            repository.setStatus(bebop, WatchStatus.PLAN_TO_WATCH)

            val row = db.listEntryQueries.selectByMalId(bebop.toLong()).executeAsOne()
            assertEquals(null, row.deleted_at)
            assertEquals("PLAN_TO_WATCH", row.status)
        }

    @Test
    fun invalidStatusIsRejectedByCheckConstraint() {
        assertFailsWith<Exception> {
            // Plain INSERT: insertPending uses OR IGNORE, which would swallow the CHECK violation.
            driver.execute(
                null,
                "INSERT INTO list_entry (id, mal_id, status, updated_at) " +
                    "VALUES ('id', $bebop, 'BINGING', 0)",
                0,
            )
        }
    }
}
