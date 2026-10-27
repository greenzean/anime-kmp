package com.greenzean.animekmp.shared.library.data

import app.cash.turbine.test
import com.greenzean.animekmp.shared.core.data.TestAnimeCatalog
import com.greenzean.animekmp.shared.core.data.createTestDriver
import com.greenzean.animekmp.shared.core.data.db.AnimeDatabase
import com.greenzean.animekmp.shared.library.domain.Anime
import com.greenzean.animekmp.shared.library.domain.SyncState
import com.greenzean.animekmp.shared.library.domain.WatchStatus
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SqlDelightLibraryDataSourceTest {
    private val driver = createTestDriver()
    private val db = AnimeDatabase(driver).also(TestAnimeCatalog::apply)
    private val repository = SqlDelightLibraryDataSource(db, dispatcher = UnconfinedTestDispatcher())
    private val bebop = 1

    @AfterTest
    fun tearDown() = driver.close()

    @Test
    fun saveEntryForUnknownAnimeFailsForeignKey() =
        runTest {
            assertFailsWith<Exception> { repository.saveEntry(999, WatchStatus.WATCHING, 0, null) }
        }

    @Test
    fun saveAnimeStoresTitlesAndImageForTheLibrary() =
        runTest {
            repository.saveAnime(
                Anime(52991, "Sousou no Frieren", "Frieren: Beyond Journey's End", "https://img/52991.jpg"),
            )

            repository.saveEntry(52991, WatchStatus.PLAN_TO_WATCH, 0, null)

            repository.observeLibrary().test {
                val anime = awaitItem().single().anime
                assertEquals("Sousou no Frieren", anime.title)
                assertEquals("Frieren: Beyond Journey's End", anime.titleEn)
                assertEquals("https://img/52991.jpg", anime.imageUrl)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun saveAnimeAgainKeepsTheImageWhenTheNewCopyHasNone() =
        runTest {
            repository.saveAnime(Anime(52991, "Sousou no Frieren", "Frieren", "https://img/52991.jpg"))

            repository.saveAnime(Anime(52991, "Sousou no Frieren", null, null))

            val row = db.animeQueries.selectByMalId(52991).executeAsOne()
            assertEquals("Frieren", row.title_en)
            assertEquals("https://img/52991.jpg", row.image_url)
        }

    @Test
    fun saveEntryAddsPendingEntry() =
        runTest {
            repository.observeLibrary().test {
                assertTrue(awaitItem().isEmpty())

                repository.saveEntry(bebop, WatchStatus.WATCHING, 0, null)

                val entry = awaitItem().single()
                assertEquals("Cowboy Bebop", entry.anime.title)
                assertEquals(WatchStatus.WATCHING, entry.status)
                assertEquals(SyncState.PENDING, entry.syncState)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun saveEntryTwiceUpdatesTheSameEntry() =
        runTest {
            repository.saveEntry(bebop, WatchStatus.WATCHING, 0, null)
            val id =
                db.listEntryQueries
                    .selectPending()
                    .executeAsOne()
                    .id

            repository.saveEntry(bebop, WatchStatus.COMPLETED, 0, null)

            val row = db.listEntryQueries.selectPending().executeAsOne()
            assertEquals(id, row.id)
            assertEquals("COMPLETED", row.status)
        }

    @Test
    fun markSyncedClearsPendingUntilNextLocalWrite() =
        runTest {
            repository.saveEntry(bebop, WatchStatus.WATCHING, 0, null)
            val row = db.listEntryQueries.selectPending().executeAsOne()

            db.listEntryQueries.markSynced(row.id, row.updated_at)
            assertTrue(
                db.listEntryQueries
                    .selectPending()
                    .executeAsList()
                    .isEmpty(),
            )

            repository.saveEntry(bebop, WatchStatus.DROPPED, 0, null)
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
            repository.saveEntry(bebop, WatchStatus.WATCHING, 0, null)

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
            repository.saveEntry(bebop, WatchStatus.WATCHING, 0, null)
            repository.remove(bebop)

            repository.saveEntry(bebop, WatchStatus.PLAN_TO_WATCH, 0, null)

            val row = db.listEntryQueries.selectByMalId(bebop.toLong()).executeAsOne()
            assertEquals(null, row.deleted_at)
            assertEquals("PLAN_TO_WATCH", row.status)
        }

    @Test
    fun invalidStatusIsRejectedByCheckConstraint() {
        assertFailsWith<Exception> {
            driver.execute(
                null,
                "INSERT INTO list_entry (id, mal_id, status, updated_at) " +
                    "VALUES ('id', $bebop, 'BINGING', 0)",
                0,
            )
        }
    }

    @Test
    fun saveEntryStoresProgressAndScore() =
        runTest {
            repository.saveEntry(bebop, WatchStatus.WATCHING, 7, 9)

            val row = db.listEntryQueries.selectByMalId(bebop.toLong()).executeAsOne()
            assertEquals(7L, row.progress)
            assertEquals(9L, row.score)
            assertEquals("PENDING", row.sync_state)
        }

    @Test
    fun saveEntryWithNoScoreStoresNull() =
        runTest {
            repository.saveEntry(bebop, WatchStatus.PLAN_TO_WATCH, 0, null)

            val row = db.listEntryQueries.selectByMalId(bebop.toLong()).executeAsOne()
            assertEquals(0L, row.progress)
            assertNull(row.score)
        }

    @Test
    fun saveEntryAgainUpdatesProgressAndScoreInPlace() =
        runTest {
            repository.saveEntry(bebop, WatchStatus.WATCHING, 2, null)
            val id =
                db.listEntryQueries
                    .selectByMalId(bebop.toLong())
                    .executeAsOne()
                    .id

            repository.saveEntry(bebop, WatchStatus.COMPLETED, 26, 10)

            val row = db.listEntryQueries.selectByMalId(bebop.toLong()).executeAsOne()
            assertEquals(id, row.id)
            assertEquals(26L, row.progress)
            assertEquals(10L, row.score)
            assertEquals("COMPLETED", row.status)
        }

    @Test
    fun saveEntryClearsAPreviousScore() =
        runTest {
            repository.saveEntry(bebop, WatchStatus.WATCHING, 2, 8)

            repository.saveEntry(bebop, WatchStatus.WATCHING, 2, null)

            assertNull(
                db.listEntryQueries
                    .selectByMalId(bebop.toLong())
                    .executeAsOne()
                    .score,
            )
        }

    @Test
    fun observeLibraryShowsProgressScoreAndEpisodes() =
        runTest {
            repository.saveAnime(Anime(52991, "Sousou no Frieren", "Frieren", null, episodes = 28))
            repository.saveEntry(52991, WatchStatus.WATCHING, 5, 9)

            repository.observeLibrary().test {
                val entry = awaitItem().single()
                assertEquals(5, entry.progress)
                assertEquals(9, entry.score)
                assertEquals(28, entry.anime.episodes)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun saveAnimeKeepsKnownEpisodesWhenTheNewCopyHasNone() =
        runTest {
            repository.saveAnime(Anime(52991, "Sousou no Frieren", "Frieren", null, episodes = 28))

            repository.saveAnime(Anime(52991, "Sousou no Frieren", "Frieren", null, episodes = null))

            assertEquals(
                28L,
                db.animeQueries
                    .selectByMalId(52991)
                    .executeAsOne()
                    .episodes,
            )
        }

    @Test
    fun saveAnimeUpdatesEpisodesWhenTheyChange() =
        runTest {
            repository.saveAnime(Anime(52991, "Sousou no Frieren", episodes = 12))

            repository.saveAnime(Anime(52991, "Sousou no Frieren", episodes = 28))

            assertEquals(
                28L,
                db.animeQueries
                    .selectByMalId(52991)
                    .executeAsOne()
                    .episodes,
            )
        }

    @Test
    fun getEntryReturnsTheEntryWithItsAnime() =
        runTest {
            repository.saveEntry(bebop, WatchStatus.WATCHING, 3, 6)

            val entry = repository.getEntry(bebop)!!

            assertEquals("Cowboy Bebop", entry.anime.title)
            assertEquals(WatchStatus.WATCHING, entry.status)
            assertEquals(3, entry.progress)
            assertEquals(6, entry.score)
            assertEquals(SyncState.PENDING, entry.syncState)
        }

    @Test
    fun getEntryIsNullForAnimeNotInTheLibrary() =
        runTest {
            assertNull(repository.getEntry(bebop))
        }

    @Test
    fun getEntryIsNullAfterRemove() =
        runTest {
            repository.saveEntry(bebop, WatchStatus.WATCHING, 3, 6)

            repository.remove(bebop)

            assertNull(repository.getEntry(bebop))
        }

    @Test
    fun restoreWithSaveEntryRevivesTheTombstoneWithItsValues() =
        runTest {
            repository.saveEntry(bebop, WatchStatus.WATCHING, 3, 6)
            val snapshot = repository.getEntry(bebop)!!
            repository.remove(bebop)

            repository.saveEntry(bebop, snapshot.status, snapshot.progress, snapshot.score)

            val row = db.listEntryQueries.selectByMalId(bebop.toLong()).executeAsOne()
            assertNull(row.deleted_at)
            assertEquals(snapshot.id, row.id)
            assertEquals(3L, row.progress)
            assertEquals(6L, row.score)
            assertEquals("PENDING", row.sync_state)
            assertEquals(snapshot.status, repository.getEntry(bebop)!!.status)
        }

    @Test
    fun removeKeepsProgressAndScoreOnTheTombstone() =
        runTest {
            repository.saveEntry(bebop, WatchStatus.WATCHING, 3, 6)

            repository.remove(bebop)

            val tombstone = db.listEntryQueries.selectPending().executeAsOne()
            assertEquals(3L, tombstone.progress)
            assertEquals(6L, tombstone.score)
        }
}
