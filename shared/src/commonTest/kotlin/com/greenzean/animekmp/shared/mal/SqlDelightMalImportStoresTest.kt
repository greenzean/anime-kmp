package com.greenzean.animekmp.shared.mal

import com.greenzean.animekmp.shared.core.data.TestAnimeCatalog
import com.greenzean.animekmp.shared.core.data.createTestDriver
import com.greenzean.animekmp.shared.core.data.db.AnimeDatabase
import com.greenzean.animekmp.shared.library.data.SqlDelightLibraryDataSource
import com.greenzean.animekmp.shared.library.domain.WatchStatus
import com.greenzean.animekmp.shared.mal.data.SqlDelightMalImportProgressStore
import com.greenzean.animekmp.shared.mal.data.SqlDelightOnMalDataSource
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

class SqlDelightMalImportStoresTest {
    private val driver = createTestDriver()
    private val db = AnimeDatabase(driver).also(TestAnimeCatalog::apply)
    private val dispatcher = UnconfinedTestDispatcher()
    private val clock =
        object : Clock {
            override fun now(): Instant = Instant.fromEpochMilliseconds(0)
        }
    private val library = SqlDelightLibraryDataSource(db, clock, dispatcher)
    private val progress = SqlDelightMalImportProgressStore(db, dispatcher)
    private val onMal = SqlDelightOnMalDataSource(db, dispatcher)
    private val bebop = 1
    private val steinsGate = 9253

    @AfterTest
    fun tearDown() = driver.close()

    private fun onMalFlag(malId: Int): Long =
        db.listEntryQueries
            .selectByMalId(malId.toLong())
            .executeAsOne()
            .on_mal

    @Test
    fun offsetIsZeroByDefault() =
        runTest {
            assertEquals(0, progress.offset())
        }

    @Test
    fun savedOffsetIsReadBack() =
        runTest {
            progress.setOffset(100)

            assertEquals(100, progress.offset())
        }

    @Test
    fun savingAgainReplacesTheOffset() =
        runTest {
            progress.setOffset(100)
            progress.setOffset(300)

            assertEquals(300, progress.offset())
            assertEquals(
                1,
                db.malImportProgressQueries
                    .selectOffset()
                    .executeAsList()
                    .size,
            )
        }

    @Test
    fun clearResetsTheOffsetToZero() =
        runTest {
            progress.setOffset(200)

            progress.clear()

            assertEquals(0, progress.offset())
        }

    @Test
    fun clearingWithNothingStoredIsHarmless() =
        runTest {
            progress.clear()

            assertEquals(0, progress.offset())
        }

    @Test
    fun entriesAreNotOnMalByDefault() =
        runTest {
            library.saveEntry(bebop, WatchStatus.WATCHING, 5, 7)

            assertEquals(0L, onMalFlag(bebop))
        }

    @Test
    fun markOnMalSetsOnlyThatEntry() =
        runTest {
            library.saveEntry(bebop, WatchStatus.WATCHING, 5, 7)
            library.saveEntry(steinsGate, WatchStatus.WATCHING, 1, null)

            onMal.markOnMal(bebop)

            assertEquals(1L, onMalFlag(bebop))
            assertEquals(0L, onMalFlag(steinsGate))
        }

    @Test
    fun markingAnUnknownEntryIsHarmless() =
        runTest {
            onMal.markOnMal(bebop)

            assertEquals(
                0,
                db.listEntryQueries
                    .selectByMalId(bebop.toLong())
                    .executeAsList()
                    .size,
            )
        }

    @Test
    fun editingAMarkedEntryKeepsTheMarker() =
        runTest {
            library.saveEntry(bebop, WatchStatus.WATCHING, 5, 7)
            onMal.markOnMal(bebop)

            library.saveEntry(bebop, WatchStatus.COMPLETED, 26, 9)

            assertEquals(1L, onMalFlag(bebop))
        }

    @Test
    fun activeOnMalIdsIsEmptyByDefault() =
        runTest {
            library.saveEntry(bebop, WatchStatus.WATCHING, 5, 7)

            assertEquals(emptyList(), onMal.activeOnMalIds())
        }

    @Test
    fun activeOnMalIdsListsOnlyMarkedEntries() =
        runTest {
            library.saveEntry(bebop, WatchStatus.WATCHING, 5, 7)
            library.saveEntry(steinsGate, WatchStatus.WATCHING, 1, null)
            onMal.markOnMal(bebop)

            assertEquals(listOf(bebop), onMal.activeOnMalIds())
        }

    @Test
    fun activeOnMalIdsLeavesOutRemovedEntries() =
        runTest {
            library.saveEntry(bebop, WatchStatus.WATCHING, 5, 7)
            library.saveEntry(steinsGate, WatchStatus.WATCHING, 1, null)
            onMal.markOnMal(bebop)
            onMal.markOnMal(steinsGate)
            library.remove(bebop)

            assertEquals(listOf(steinsGate), onMal.activeOnMalIds())
        }
}
