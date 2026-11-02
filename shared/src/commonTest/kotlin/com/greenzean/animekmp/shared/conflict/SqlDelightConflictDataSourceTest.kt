package com.greenzean.animekmp.shared.conflict

import app.cash.turbine.test
import com.greenzean.animekmp.shared.conflict.data.SqlDelightConflictDataSource
import com.greenzean.animekmp.shared.conflict.domain.Conflict
import com.greenzean.animekmp.shared.core.data.TestAnimeCatalog
import com.greenzean.animekmp.shared.core.data.createTestDriver
import com.greenzean.animekmp.shared.core.data.db.AnimeDatabase
import com.greenzean.animekmp.shared.library.data.SqlDelightLibraryDataSource
import com.greenzean.animekmp.shared.library.domain.Anime
import com.greenzean.animekmp.shared.library.domain.WatchStatus
import com.greenzean.animekmp.shared.sync.data.SqlDelightAccountLibraryDataSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class SqlDelightConflictDataSourceTest {
    private val driver = createTestDriver()
    private val db = AnimeDatabase(driver).also(TestAnimeCatalog::apply)
    private var now = 0L
    private val clock =
        object : Clock {
            override fun now(): Instant = Instant.fromEpochMilliseconds(now)
        }
    private val dispatcher = UnconfinedTestDispatcher()
    private val library = SqlDelightLibraryDataSource(db, clock, dispatcher)
    private val conflicts = SqlDelightConflictDataSource(db, clock, dispatcher)
    private val account = SqlDelightAccountLibraryDataSource(db, dispatcher)
    private val bebop = 1
    private val steinsGate = 9253

    @AfterTest
    fun tearDown() = driver.close()

    private suspend fun observed(): List<Conflict> = conflicts.observeConflicts().first()

    @Test
    fun noConflictsByDefault() =
        runTest {
            assertTrue(observed().isEmpty())
            assertTrue(conflicts.getConflicts().isEmpty())
        }

    @Test
    fun observeJoinsTheAppSideAndTheAnimeTitle() =
        runTest {
            library.saveEntry(bebop, WatchStatus.WATCHING, 5, 7)

            conflicts.saveConflict(bebop, values(WatchStatus.COMPLETED, 26, 9))

            val conflict = observed().single()
            assertEquals(bebop, conflict.malId)
            assertEquals("Cowboy Bebop", conflict.title)
            assertNull(conflict.episodes)
            assertEquals(values(WatchStatus.WATCHING, 5, 7), conflict.app)
            assertEquals(values(WatchStatus.COMPLETED, 26, 9), conflict.other)
            assertNull(conflict.imageUrl)
        }

    @Test
    fun observeCarriesTheAnimeImage() =
        runTest {
            val withImage = 777
            library.saveAnime(Anime(withImage, "Trigun", imageUrl = "https://img.example/trigun.jpg"))
            library.saveEntry(withImage, WatchStatus.WATCHING, 5, 7)

            conflicts.saveConflict(withImage, values(WatchStatus.COMPLETED, 26, 9))

            assertEquals("https://img.example/trigun.jpg", observed().single().imageUrl)
        }

    @Test
    fun getConflictsReturnsTheSameAsObserve() =
        runTest {
            library.saveEntry(bebop, WatchStatus.WATCHING, 5, 7)
            conflicts.saveConflict(bebop, values())

            assertEquals(observed(), conflicts.getConflicts())
        }

    @Test
    fun nullScoreRoundTrips() =
        runTest {
            library.saveEntry(bebop, WatchStatus.WATCHING, 5, null)

            conflicts.saveConflict(bebop, values(WatchStatus.DROPPED, 3, null))

            val conflict = observed().single()
            assertNull(conflict.app.score)
            assertNull(conflict.other.score)
        }

    @Test
    fun savingAgainForTheSameAnimeReplacesTheOtherSide() =
        runTest {
            library.saveEntry(bebop, WatchStatus.WATCHING, 5, 7)
            conflicts.saveConflict(bebop, values(WatchStatus.COMPLETED, 26, 9))

            conflicts.saveConflict(bebop, values(WatchStatus.DROPPED, 8, 2))

            assertEquals(values(WatchStatus.DROPPED, 8, 2), observed().single().other)
        }

    @Test
    fun removedAppEntryShowsTheAppSideAsRemoved() =
        runTest {
            library.saveEntry(bebop, WatchStatus.WATCHING, 5, 7)
            library.remove(bebop)

            conflicts.saveConflict(bebop, values(WatchStatus.COMPLETED, 26, 9))

            val conflict = observed().single()
            assertTrue(conflict.app.removed)
            assertEquals(values(WatchStatus.COMPLETED, 26, 9), conflict.other)
        }

    @Test
    fun missingAppRowCountsAsRemoved() =
        runTest {
            conflicts.saveConflict(bebop, values())

            assertTrue(observed().single().app.removed)
        }

    @Test
    fun conflictWhoseSidesNowMatchIsFilteredOut() =
        runTest {
            library.saveEntry(bebop, WatchStatus.COMPLETED, 26, 9)

            conflicts.saveConflict(bebop, values(WatchStatus.COMPLETED, 26, 9))

            assertTrue(observed().isEmpty())
            assertTrue(conflicts.getConflicts().isEmpty())
        }

    @Test
    fun editingTheEntryToMatchRemovesTheConflictFromTheObservedList() =
        runTest {
            library.saveEntry(bebop, WatchStatus.WATCHING, 5, 7)
            conflicts.saveConflict(bebop, values(WatchStatus.COMPLETED, 26, 9))

            conflicts.observeConflicts().test {
                assertEquals(1, awaitItem().size)
                library.saveEntry(bebop, WatchStatus.COMPLETED, 26, 9)
                assertTrue(awaitItem().isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun deleteConflictRemovesOnlyThatOne() =
        runTest {
            library.saveEntry(bebop, WatchStatus.WATCHING, 5, 7)
            library.saveEntry(steinsGate, WatchStatus.WATCHING, 1, null)
            conflicts.saveConflict(bebop, values())
            conflicts.saveConflict(steinsGate, values())

            conflicts.deleteConflict(bebop)

            assertEquals(listOf(steinsGate), observed().map { it.malId })
        }

    @Test
    fun deletingAMissingConflictIsHarmless() =
        runTest {
            conflicts.deleteConflict(bebop)

            assertTrue(observed().isEmpty())
        }

    @Test
    fun clearLibraryDeletesTheConflicts() =
        runTest {
            library.saveEntry(bebop, WatchStatus.WATCHING, 5, 7)
            conflicts.saveConflict(bebop, values())

            account.clearLibrary()

            assertTrue(conflicts.getConflicts().isEmpty())
            assertTrue(observed().isEmpty())
        }

    @Test
    fun detachFromAccountKeepsTheConflicts() =
        runTest {
            library.saveEntry(bebop, WatchStatus.WATCHING, 5, 7)
            conflicts.saveConflict(bebop, values(WatchStatus.COMPLETED, 26, 9))

            account.detachFromAccount()

            assertEquals(listOf(bebop), conflicts.getConflicts().map { it.malId })
        }
}
