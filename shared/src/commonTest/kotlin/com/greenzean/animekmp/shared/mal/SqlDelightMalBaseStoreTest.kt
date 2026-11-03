package com.greenzean.animekmp.shared.mal

import com.greenzean.animekmp.shared.core.data.createTestDriver
import com.greenzean.animekmp.shared.core.data.db.AnimeDatabase
import com.greenzean.animekmp.shared.library.domain.WatchStatus
import com.greenzean.animekmp.shared.mal.data.SqlDelightMalBaseStore
import com.greenzean.animekmp.shared.mal.domain.MalBaseValues
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SqlDelightMalBaseStoreTest {
    private val driver = createTestDriver()
    private val db = AnimeDatabase(driver)
    private val dispatcher = UnconfinedTestDispatcher()
    private val store = SqlDelightMalBaseStore(db, dispatcher)

    @AfterTest
    fun tearDown() = driver.close()

    @Test
    fun nothingIsStoredByDefault() =
        runTest {
            assertNull(store.get(1))
        }

    @Test
    fun savedValuesAreReadBack() =
        runTest {
            store.save(1, MalBaseValues(WatchStatus.WATCHING, 5, 7))

            assertEquals(MalBaseValues(WatchStatus.WATCHING, 5, 7), store.get(1))
        }

    @Test
    fun aNullScoreSurvivesTheRoundTrip() =
        runTest {
            store.save(1, MalBaseValues(WatchStatus.PLAN_TO_WATCH, 0, null))

            assertEquals(MalBaseValues(WatchStatus.PLAN_TO_WATCH, 0, null), store.get(1))
        }

    @Test
    fun everyStatusSurvivesTheRoundTrip() =
        runTest {
            WatchStatus.entries.forEachIndexed {
                index,
                status,
                ->
                store.save(index + 1, MalBaseValues(status, index, null))
            }

            WatchStatus.entries.forEachIndexed { index, status ->
                assertEquals(MalBaseValues(status, index, null), store.get(index + 1))
            }
        }

    @Test
    fun savingAgainReplacesTheValues() =
        runTest {
            store.save(1, MalBaseValues(WatchStatus.WATCHING, 5, 7))

            store.save(1, MalBaseValues(WatchStatus.COMPLETED, 26, null))

            assertEquals(MalBaseValues(WatchStatus.COMPLETED, 26, null), store.get(1))
        }

    @Test
    fun entriesAreKeptApart() =
        runTest {
            store.save(1, MalBaseValues(WatchStatus.WATCHING, 5, 7))
            store.save(2, MalBaseValues(WatchStatus.DROPPED, 1, 3))

            assertEquals(MalBaseValues(WatchStatus.WATCHING, 5, 7), store.get(1))
            assertEquals(MalBaseValues(WatchStatus.DROPPED, 1, 3), store.get(2))
        }

    @Test
    fun deleteRemovesOnlyThatEntry() =
        runTest {
            store.save(1, MalBaseValues(WatchStatus.WATCHING, 5, 7))
            store.save(2, MalBaseValues(WatchStatus.DROPPED, 1, 3))

            store.delete(1)

            assertNull(store.get(1))
            assertEquals(MalBaseValues(WatchStatus.DROPPED, 1, 3), store.get(2))
        }

    @Test
    fun deletingAMissingEntryIsHarmless() =
        runTest {
            store.delete(9)

            assertNull(store.get(9))
        }

    @Test
    fun clearRemovesEverything() =
        runTest {
            store.save(1, MalBaseValues(WatchStatus.WATCHING, 5, 7))
            store.save(2, MalBaseValues(WatchStatus.DROPPED, 1, 3))

            store.clear()

            assertNull(store.get(1))
            assertNull(store.get(2))
        }
}
