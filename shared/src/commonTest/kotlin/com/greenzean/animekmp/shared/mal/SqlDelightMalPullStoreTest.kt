package com.greenzean.animekmp.shared.mal

import app.cash.turbine.test
import com.greenzean.animekmp.shared.core.data.createTestDriver
import com.greenzean.animekmp.shared.core.data.db.AnimeDatabase
import com.greenzean.animekmp.shared.mal.data.SqlDelightMalPullStore
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class SqlDelightMalPullStoreTest {
    private val driver = createTestDriver()
    private val db = AnimeDatabase(driver)
    private val dispatcher = UnconfinedTestDispatcher()
    private val store = SqlDelightMalPullStore(db, dispatcher)
    private val first = Instant.parse("2026-11-03T12:00:00Z")
    private val second = Instant.parse("2026-11-03T18:30:15.250Z")

    @AfterTest
    fun tearDown() = driver.close()

    @Test
    fun nothingIsStoredByDefault() =
        runTest {
            assertNull(store.lastPulledAt())
        }

    @Test
    fun aSavedTimeIsReadBack() =
        runTest {
            store.setLastPulledAt(first)

            assertEquals(first, store.lastPulledAt())
        }

    @Test
    fun theTimeKeepsItsMilliseconds() =
        runTest {
            store.setLastPulledAt(second)

            assertEquals(second, store.lastPulledAt())
        }

    @Test
    fun savingAgainReplacesTheTimeAndKeepsASingleRow() =
        runTest {
            store.setLastPulledAt(first)

            store.setLastPulledAt(second)

            assertEquals(second, store.lastPulledAt())
            assertEquals(
                1,
                db.malPullQueries
                    .selectLastPulledAt()
                    .executeAsList()
                    .size,
            )
        }

    @Test
    fun clearForgetsTheTime() =
        runTest {
            store.setLastPulledAt(first)

            store.clear()

            assertNull(store.lastPulledAt())
        }

    @Test
    fun clearingWithNothingStoredIsHarmless() =
        runTest {
            store.clear()

            assertNull(store.lastPulledAt())
        }

    @Test
    fun aTimeCanBeSavedAfterClearing() =
        runTest {
            store.setLastPulledAt(first)
            store.clear()

            store.setLastPulledAt(second)

            assertEquals(second, store.lastPulledAt())
        }

    @Test
    fun observeEmitsNullThenEachSavedTimeThenNullAfterClear() =
        runTest {
            store.observeLastPulledAt().test {
                assertNull(awaitItem())
                store.setLastPulledAt(first)
                assertEquals(first, awaitItem())
                store.setLastPulledAt(second)
                assertEquals(second, awaitItem())
                store.clear()
                assertNull(awaitItem())
            }
        }
}
