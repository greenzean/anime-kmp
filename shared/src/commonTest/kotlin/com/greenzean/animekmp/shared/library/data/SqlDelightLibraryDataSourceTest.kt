package com.greenzean.animekmp.shared.library.data

import app.cash.turbine.test
import com.greenzean.animekmp.shared.core.data.TestAnimeCatalog
import com.greenzean.animekmp.shared.core.data.createTestDriver
import com.greenzean.animekmp.shared.core.data.db.AnimeDatabase
import com.greenzean.animekmp.shared.library.domain.Anime
import com.greenzean.animekmp.shared.library.domain.LibraryEntry
import com.greenzean.animekmp.shared.library.domain.LibrarySort
import com.greenzean.animekmp.shared.library.domain.SyncState
import com.greenzean.animekmp.shared.library.domain.WatchStatus
import com.greenzean.animekmp.shared.settings.domain.TitleLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class SqlDelightLibraryDataSourceTest {
    private val driver = createTestDriver()
    private val db = AnimeDatabase(driver).also(TestAnimeCatalog::apply)
    private var now = 0L
    private val clock =
        object : Clock {
            override fun now(): Instant = Instant.fromEpochMilliseconds(now)
        }
    private val repository = SqlDelightLibraryDataSource(db, clock, UnconfinedTestDispatcher())
    private val bebop = 1
    private val frieren = Anime(52991, "Sousou no Frieren", "Frieren: Beyond Journey's End")
    private val steinsGate = 9253
    private val fullmetal = 5114

    private fun observe(
        query: String = "",
        status: WatchStatus? = null,
        sort: LibrarySort = LibrarySort.LAST_UPDATED,
        language: TitleLanguage = TitleLanguage.MAIN,
    ): Flow<List<LibraryEntry>> = repository.observeLibrary(query, status, sort, language)

    private suspend fun titles(
        query: String = "",
        status: WatchStatus? = null,
        sort: LibrarySort = LibrarySort.LAST_UPDATED,
        language: TitleLanguage = TitleLanguage.MAIN,
    ): List<Int> = observe(query, status, sort, language).first().map { it.anime.malId }

    private suspend fun add(
        malId: Int,
        at: Long,
        status: WatchStatus = WatchStatus.WATCHING,
        score: Int? = null,
    ) {
        now = at
        repository.saveEntry(malId, status, 0, score)
    }

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

            observe().test {
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
            observe().test {
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

            observe().test {
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

            observe().test {
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

    @Test
    fun blankQueryMatchesEveryActiveEntry() =
        runTest {
            add(bebop, 1)
            add(steinsGate, 2)

            assertEquals(setOf(bebop, steinsGate), titles(query = "").toSet())
            assertEquals(setOf(bebop, steinsGate), titles(query = "   ").toSet())
        }

    @Test
    fun searchMatchesASubstringOfTheMainTitle() =
        runTest {
            add(bebop, 1)
            add(steinsGate, 2)

            assertEquals(listOf(bebop), titles(query = "bebop"))
        }

    @Test
    fun searchMatchesTheEnglishTitleEvenWhenTheMainTitleIsDisplayed() =
        runTest {
            repository.saveAnime(frieren)
            add(frieren.malId, 1)
            add(bebop, 2)

            assertEquals(listOf(frieren.malId), titles(query = "beyond", language = TitleLanguage.MAIN))
            assertEquals(listOf(frieren.malId), titles(query = "sousou", language = TitleLanguage.ENGLISH))
        }

    @Test
    fun searchIsCaseInsensitive() =
        runTest {
            add(bebop, 1)
            add(steinsGate, 2)

            assertEquals(listOf(bebop), titles(query = "COWBOY"))
            assertEquals(listOf(steinsGate), titles(query = "steins;GATE"))
        }

    @Test
    fun searchWithNoMatchIsEmpty() =
        runTest {
            add(bebop, 1)

            assertTrue(titles(query = "naruto").isEmpty())
        }

    @Test
    fun percentInTheQueryIsTreatedLiterally() =
        runTest {
            repository.saveAnime(Anime(100, "100% Pascal-sensei", null))
            repository.saveAnime(Anime(101, "Plain Title", null))
            add(100, 1)
            add(101, 2)

            assertEquals(listOf(100), titles(query = "100%"))
            assertEquals(listOf(100), titles(query = "%"))
        }

    @Test
    fun underscoreInTheQueryIsTreatedLiterally() =
        runTest {
            repository.saveAnime(Anime(102, "Re_Zero", null))
            repository.saveAnime(Anime(103, "ReXZero", null))
            add(102, 1)
            add(103, 2)

            assertEquals(listOf(102), titles(query = "re_zero"))
        }

    @Test
    fun statusFilterKeepsOnlyThatStatus() =
        runTest {
            add(bebop, 1, WatchStatus.WATCHING)
            add(steinsGate, 2, WatchStatus.COMPLETED)
            add(fullmetal, 3, WatchStatus.COMPLETED)

            assertEquals(setOf(steinsGate, fullmetal), titles(status = WatchStatus.COMPLETED).toSet())
            assertEquals(listOf(bebop), titles(status = WatchStatus.WATCHING))
            assertTrue(titles(status = WatchStatus.DROPPED).isEmpty())
        }

    @Test
    fun nullStatusShowsEveryStatus() =
        runTest {
            add(bebop, 1, WatchStatus.WATCHING)
            add(steinsGate, 2, WatchStatus.COMPLETED)

            assertEquals(2, titles(status = null).size)
        }

    @Test
    fun queryAndStatusCombine() =
        runTest {
            add(bebop, 1, WatchStatus.WATCHING)
            add(steinsGate, 2, WatchStatus.COMPLETED)
            add(fullmetal, 3, WatchStatus.WATCHING)

            assertEquals(
                listOf(bebop, fullmetal),
                titles(query = "o", status = WatchStatus.WATCHING, sort = LibrarySort.TITLE),
            )
            assertEquals(listOf(steinsGate), titles(query = "steins", status = WatchStatus.COMPLETED))
            assertTrue(titles(query = "steins", status = WatchStatus.WATCHING).isEmpty())
        }

    @Test
    fun lastUpdatedSortsNewestFirst() =
        runTest {
            add(bebop, 10)
            add(steinsGate, 30)
            add(fullmetal, 20)

            assertEquals(listOf(steinsGate, fullmetal, bebop), titles(sort = LibrarySort.LAST_UPDATED))
        }

    @Test
    fun editingAnEntryMovesItToTheTopOfLastUpdated() =
        runTest {
            add(bebop, 10)
            add(steinsGate, 20)

            add(bebop, 30, WatchStatus.COMPLETED)

            assertEquals(listOf(bebop, steinsGate), titles(sort = LibrarySort.LAST_UPDATED))
        }

    @Test
    fun titleSortIsAscendingByTheMainTitle() =
        runTest {
            add(steinsGate, 1)
            add(bebop, 2)
            add(fullmetal, 3)

            assertEquals(
                listOf(bebop, fullmetal, steinsGate),
                titles(sort = LibrarySort.TITLE, language = TitleLanguage.MAIN),
            )
        }

    @Test
    fun titleSortIsCaseInsensitive() =
        runTest {
            repository.saveAnime(Anime(200, "banana", null))
            repository.saveAnime(Anime(201, "Apple", null))
            repository.saveAnime(Anime(202, "cherry", null))
            add(200, 1)
            add(201, 2)
            add(202, 3)

            assertEquals(listOf(201, 200, 202), titles(sort = LibrarySort.TITLE))
        }

    @Test
    fun titleSortUsesTheEnglishTitleWhenEnglishIsDisplayed() =
        runTest {
            repository.saveAnime(Anime(300, "Zzz Main", "Alpha English"))
            repository.saveAnime(Anime(301, "Aaa Main", "Omega English"))
            add(300, 1)
            add(301, 2)

            assertEquals(listOf(301, 300), titles(sort = LibrarySort.TITLE, language = TitleLanguage.MAIN))
            assertEquals(listOf(300, 301), titles(sort = LibrarySort.TITLE, language = TitleLanguage.ENGLISH))
        }

    @Test
    fun titleSortFallsBackToTheMainTitleWhenThereIsNoEnglishTitle() =
        runTest {
            repository.saveAnime(Anime(300, "Zzz Main", "Alpha English"))
            repository.saveAnime(Anime(301, "Beta Main", null))
            add(300, 1)
            add(301, 2)

            assertEquals(listOf(300, 301), titles(sort = LibrarySort.TITLE, language = TitleLanguage.ENGLISH))
        }

    @Test
    fun scoreSortIsDescendingWithUnscoredLast() =
        runTest {
            add(bebop, 1, score = null)
            add(steinsGate, 2, score = 7)
            add(fullmetal, 3, score = 10)
            add(16498, 4, score = null)

            val result = titles(sort = LibrarySort.SCORE)

            assertEquals(listOf(fullmetal, steinsGate), result.take(2))
            assertEquals(setOf(bebop, 16498), result.drop(2).toSet())
        }

    @Test
    fun removedEntriesAreNeverShown() =
        runTest {
            add(bebop, 1)
            add(steinsGate, 2)

            repository.remove(bebop)

            assertEquals(listOf(steinsGate), titles())
            assertTrue(titles(query = "bebop").isEmpty())
            assertEquals(listOf(steinsGate), titles(status = WatchStatus.WATCHING, sort = LibrarySort.SCORE))
        }

    @Test
    fun observeActiveCountCountsOnlyActiveEntries() =
        runTest {
            repository.observeActiveCount().test {
                assertEquals(0, awaitItem())

                add(bebop, 1)
                assertEquals(1, awaitItem())

                add(steinsGate, 2)
                assertEquals(2, awaitItem())

                repository.remove(bebop)
                assertEquals(1, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun observeActiveCountIgnoresTheQueryAndFilter() =
        runTest {
            add(bebop, 1, WatchStatus.WATCHING)

            assertTrue(titles(query = "nothing").isEmpty())
            assertEquals(1, repository.observeActiveCount().first())
        }
}
