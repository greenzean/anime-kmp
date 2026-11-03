package com.greenzean.animekmp.shared.mal

import com.greenzean.animekmp.shared.conflict.data.SqlDelightConflictDataSource
import com.greenzean.animekmp.shared.conflict.domain.EntryValues
import com.greenzean.animekmp.shared.core.data.TestAnimeCatalog
import com.greenzean.animekmp.shared.core.data.createTestDriver
import com.greenzean.animekmp.shared.core.data.db.AnimeDatabase
import com.greenzean.animekmp.shared.library.data.SqlDelightLibraryDataSource
import com.greenzean.animekmp.shared.library.domain.WatchStatus
import com.greenzean.animekmp.shared.mal.data.SqlDelightMalOutboxDataSource
import com.greenzean.animekmp.shared.mal.domain.MalChange
import com.greenzean.animekmp.shared.mal.domain.MalOutboxStatus
import com.greenzean.animekmp.shared.mal.domain.MalSendError
import com.greenzean.animekmp.shared.mal.domain.QueuedMalChange
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class SqlDelightMalOutboxDataSourceTest {
    private val driver = createTestDriver()
    private val db = AnimeDatabase(driver).also(TestAnimeCatalog::apply)
    private val dispatcher = UnconfinedTestDispatcher()
    private val clock =
        object : Clock {
            private var millis = 0L

            override fun now(): Instant = Instant.fromEpochMilliseconds(millis++)
        }
    private val library = SqlDelightLibraryDataSource(db, clock, dispatcher)
    private val outbox = SqlDelightMalOutboxDataSource(db, dispatcher)
    private val conflicts = SqlDelightConflictDataSource(db, clock, dispatcher)
    private val bebop = 1
    private val fmab = 5114
    private val steinsGate = 9253

    @AfterTest
    fun tearDown() = driver.close()

    private fun onMalFlag(malId: Int): Long =
        db.listEntryQueries
            .selectByMalId(malId.toLong())
            .executeAsOne()
            .on_mal

    private suspend fun status(): MalOutboxStatus = outbox.observeStatus().first()

    private suspend fun drain(): List<MalChange> {
        val changes = mutableListOf<MalChange>()
        while (true) {
            val item = outbox.oldest() ?: return changes
            changes += item.change
            outbox.drop(item)
        }
    }

    @Test
    fun anEmptyQueueHasNoOldestAndNothingPending() =
        runTest {
            assertNull(outbox.oldest())
            assertEquals(MalOutboxStatus(0, null), status())
        }

    @Test
    fun anUpsertIsReadBackWithItsSnapshotValues() =
        runTest {
            outbox.enqueue(MalChange.Upsert(bebop, WatchStatus.WATCHING, 5, 7))

            assertEquals(MalChange.Upsert(bebop, WatchStatus.WATCHING, 5, 7), outbox.oldest()?.change)
        }

    @Test
    fun anUpsertWithoutAScoreKeepsTheScoreNull() =
        runTest {
            outbox.enqueue(MalChange.Upsert(bebop, WatchStatus.PLAN_TO_WATCH, 0, null))

            assertEquals(MalChange.Upsert(bebop, WatchStatus.PLAN_TO_WATCH, 0, null), outbox.oldest()?.change)
        }

    @Test
    fun everyStatusSurvivesTheRoundTrip() =
        runTest {
            WatchStatus.entries.forEachIndexed { index, status ->
                outbox.enqueue(MalChange.Upsert(index + 1, status, index, null))
            }

            assertEquals(
                WatchStatus.entries.mapIndexed { index, status -> MalChange.Upsert(index + 1, status, index, null) },
                drain(),
            )
        }

    @Test
    fun aRemoveIsReadBack() =
        runTest {
            outbox.enqueue(MalChange.Remove(bebop))

            assertEquals(MalChange.Remove(bebop), outbox.oldest()?.change)
        }

    @Test
    fun changesComeOutOldestFirst() =
        runTest {
            outbox.enqueue(MalChange.Upsert(steinsGate, WatchStatus.WATCHING, 1, null))
            outbox.enqueue(MalChange.Remove(bebop))
            outbox.enqueue(MalChange.Upsert(fmab, WatchStatus.COMPLETED, 64, 10))

            assertEquals(
                listOf(
                    MalChange.Upsert(steinsGate, WatchStatus.WATCHING, 1, null),
                    MalChange.Remove(bebop),
                    MalChange.Upsert(fmab, WatchStatus.COMPLETED, 64, 10),
                ),
                drain(),
            )
        }

    @Test
    fun theLatestChangeForAnAnimeReplacesTheOlderOne() =
        runTest {
            outbox.enqueue(MalChange.Upsert(bebop, WatchStatus.WATCHING, 1, null))
            outbox.enqueue(MalChange.Upsert(bebop, WatchStatus.WATCHING, 2, 8))
            outbox.enqueue(MalChange.Remove(bebop))

            assertEquals(1, status().pendingCount)
            assertEquals(listOf<MalChange>(MalChange.Remove(bebop)), drain())
        }

    @Test
    fun aReplacementMovesToTheBackOfTheQueue() =
        runTest {
            outbox.enqueue(MalChange.Upsert(bebop, WatchStatus.WATCHING, 1, null))
            outbox.enqueue(MalChange.Upsert(fmab, WatchStatus.WATCHING, 1, null))

            outbox.enqueue(MalChange.Upsert(bebop, WatchStatus.WATCHING, 3, null))

            assertEquals(
                listOf(
                    MalChange.Upsert(fmab, WatchStatus.WATCHING, 1, null),
                    MalChange.Upsert(bebop, WatchStatus.WATCHING, 3, null),
                ),
                drain(),
            )
        }

    @Test
    fun theReplacementGetsANewerSequenceNumber() =
        runTest {
            outbox.enqueue(MalChange.Upsert(bebop, WatchStatus.WATCHING, 1, null))
            val before = outbox.oldest()!!

            outbox.enqueue(MalChange.Upsert(bebop, WatchStatus.WATCHING, 2, null))

            assertTrue(outbox.oldest()!!.seq > before.seq)
        }

    @Test
    fun pendingCountCountsTheQueuedAnime() =
        runTest {
            outbox.enqueue(MalChange.Upsert(bebop, WatchStatus.WATCHING, 1, null))
            outbox.enqueue(MalChange.Remove(fmab))

            assertEquals(2, status().pendingCount)
        }

    @Test
    fun markSentOfAnUpsertDeletesTheRowAndSetsOnMal() =
        runTest {
            library.saveEntry(bebop, WatchStatus.WATCHING, 5, 7)
            outbox.enqueue(MalChange.Upsert(bebop, WatchStatus.WATCHING, 5, 7))

            outbox.markSent(outbox.oldest()!!)

            assertNull(outbox.oldest())
            assertEquals(1L, onMalFlag(bebop))
        }

    @Test
    fun markSentOfARemoveDeletesTheRowAndClearsOnMal() =
        runTest {
            library.saveEntry(bebop, WatchStatus.WATCHING, 5, 7)
            db.listEntryQueries.markOnMal(bebop.toLong())
            library.remove(bebop)
            outbox.enqueue(MalChange.Remove(bebop))

            outbox.markSent(outbox.oldest()!!)

            assertNull(outbox.oldest())
            assertEquals(0L, onMalFlag(bebop))
        }

    @Test
    fun markSentKeepsAnEditMadeWhileTheSendWasRunning() =
        runTest {
            library.saveEntry(bebop, WatchStatus.WATCHING, 5, 7)
            outbox.enqueue(MalChange.Upsert(bebop, WatchStatus.WATCHING, 5, 7))
            val inFlight = outbox.oldest()!!
            outbox.enqueue(MalChange.Upsert(bebop, WatchStatus.WATCHING, 6, 7))

            outbox.markSent(inFlight)

            assertEquals(MalChange.Upsert(bebop, WatchStatus.WATCHING, 6, 7), outbox.oldest()?.change)
        }

    @Test
    fun dropDeletesTheRowWithoutTouchingOnMal() =
        runTest {
            library.saveEntry(bebop, WatchStatus.WATCHING, 5, 7)
            outbox.enqueue(MalChange.Upsert(bebop, WatchStatus.WATCHING, 5, 7))

            outbox.drop(outbox.oldest()!!)

            assertNull(outbox.oldest())
            assertEquals(0L, onMalFlag(bebop))
        }

    @Test
    fun dropKeepsAnEditMadeWhileTheSendWasRunning() =
        runTest {
            outbox.enqueue(MalChange.Upsert(bebop, WatchStatus.WATCHING, 5, 7))
            val inFlight = outbox.oldest()!!
            outbox.enqueue(MalChange.Upsert(bebop, WatchStatus.WATCHING, 6, 7))

            outbox.drop(inFlight)

            assertEquals(1, status().pendingCount)
        }

    @Test
    fun markFailedKeepsTheRowAndReportsTheError() =
        runTest {
            outbox.enqueue(MalChange.Upsert(bebop, WatchStatus.WATCHING, 5, 7))

            outbox.markFailed(outbox.oldest()!!, MalSendError.THROTTLED)

            assertEquals(MalOutboxStatus(1, MalSendError.THROTTLED), status())
            assertEquals(MalChange.Upsert(bebop, WatchStatus.WATCHING, 5, 7), outbox.oldest()?.change)
        }

    @Test
    fun everyErrorSurvivesTheRoundTrip() =
        runTest {
            MalSendError.entries.forEach { error ->
                outbox.enqueue(MalChange.Remove(bebop))
                outbox.markFailed(outbox.oldest()!!, error)

                assertEquals(error, status().lastError)
            }
        }

    @Test
    fun markFailedOfAnOutdatedItemLeavesTheNewerRowClean() =
        runTest {
            outbox.enqueue(MalChange.Upsert(bebop, WatchStatus.WATCHING, 5, 7))
            val inFlight = outbox.oldest()!!
            outbox.enqueue(MalChange.Upsert(bebop, WatchStatus.WATCHING, 6, 7))

            outbox.markFailed(inFlight, MalSendError.SERVER)

            assertNull(status().lastError)
        }

    @Test
    fun aNewChangeForAnAnimeClearsItsStoredError() =
        runTest {
            outbox.enqueue(MalChange.Upsert(bebop, WatchStatus.WATCHING, 5, 7))
            outbox.markFailed(outbox.oldest()!!, MalSendError.SERVER)

            outbox.enqueue(MalChange.Upsert(bebop, WatchStatus.WATCHING, 6, 7))

            assertNull(status().lastError)
        }

    @Test
    fun clearErrorsRemovesEveryErrorAndKeepsTheRows() =
        runTest {
            outbox.enqueue(MalChange.Upsert(bebop, WatchStatus.WATCHING, 5, 7))
            outbox.enqueue(MalChange.Remove(fmab))
            outbox.markFailed(outbox.oldest()!!, MalSendError.NO_CONNECTION)

            outbox.clearErrors()

            assertEquals(MalOutboxStatus(2, null), status())
        }

    @Test
    fun clearEmptiesTheQueue() =
        runTest {
            outbox.enqueue(MalChange.Upsert(bebop, WatchStatus.WATCHING, 5, 7))
            outbox.enqueue(MalChange.Remove(fmab))

            outbox.clear()

            assertNull(outbox.oldest())
            assertEquals(MalOutboxStatus(0, null), status())
        }

    @Test
    fun clearingDoesNotTouchOnMal() =
        runTest {
            library.saveEntry(bebop, WatchStatus.WATCHING, 5, 7)
            db.listEntryQueries.markOnMal(bebop.toLong())
            outbox.enqueue(MalChange.Upsert(bebop, WatchStatus.WATCHING, 5, 7))

            outbox.clear()

            assertEquals(1L, onMalFlag(bebop))
        }

    @Test
    fun appOnlyEntriesAreQueuedWithTheirCurrentValues() =
        runTest {
            library.saveEntry(bebop, WatchStatus.WATCHING, 5, 7)
            library.saveEntry(steinsGate, WatchStatus.PLAN_TO_WATCH, 0, null)

            outbox.enqueueAppOnlyEntries()

            assertEquals(
                setOf<MalChange>(
                    MalChange.Upsert(bebop, WatchStatus.WATCHING, 5, 7),
                    MalChange.Upsert(steinsGate, WatchStatus.PLAN_TO_WATCH, 0, null),
                ),
                drain().toSet(),
            )
        }

    @Test
    fun entriesAlreadyOnMalAreNotQueued() =
        runTest {
            library.saveEntry(bebop, WatchStatus.WATCHING, 5, 7)
            library.saveEntry(steinsGate, WatchStatus.WATCHING, 1, null)
            db.listEntryQueries.markOnMal(bebop.toLong())

            outbox.enqueueAppOnlyEntries()

            assertEquals(listOf<MalChange>(MalChange.Upsert(steinsGate, WatchStatus.WATCHING, 1, null)), drain())
        }

    @Test
    fun removedEntriesAreNotQueued() =
        runTest {
            library.saveEntry(bebop, WatchStatus.WATCHING, 5, 7)
            library.saveEntry(steinsGate, WatchStatus.WATCHING, 1, null)
            library.remove(bebop)

            outbox.enqueueAppOnlyEntries()

            assertEquals(listOf<MalChange>(MalChange.Upsert(steinsGate, WatchStatus.WATCHING, 1, null)), drain())
        }

    @Test
    fun anAlreadyQueuedChangeIsNotReplacedByTheAppOnlyPass() =
        runTest {
            library.saveEntry(bebop, WatchStatus.WATCHING, 5, 7)
            outbox.enqueue(MalChange.Upsert(bebop, WatchStatus.COMPLETED, 26, 9))

            outbox.enqueueAppOnlyEntries()

            assertEquals(listOf<MalChange>(MalChange.Upsert(bebop, WatchStatus.COMPLETED, 26, 9)), drain())
        }

    @Test
    fun runningTheAppOnlyPassTwiceQueuesEachEntryOnce() =
        runTest {
            library.saveEntry(bebop, WatchStatus.WATCHING, 5, 7)

            outbox.enqueueAppOnlyEntries()
            outbox.enqueueAppOnlyEntries()

            assertEquals(1, status().pendingCount)
        }

    @Test
    fun appOnlyEntriesQueueBehindWhatIsAlreadyWaiting() =
        runTest {
            library.saveEntry(steinsGate, WatchStatus.WATCHING, 1, null)
            outbox.enqueue(MalChange.Remove(fmab))

            outbox.enqueueAppOnlyEntries()

            assertEquals(
                listOf(MalChange.Remove(fmab), MalChange.Upsert(steinsGate, WatchStatus.WATCHING, 1, null)),
                drain(),
            )
        }

    @Test
    fun theAppOnlyPassAssignsDistinctSequenceNumbers() =
        runTest {
            library.saveEntry(bebop, WatchStatus.WATCHING, 5, 7)
            library.saveEntry(steinsGate, WatchStatus.WATCHING, 1, null)
            library.saveEntry(fmab, WatchStatus.COMPLETED, 64, 10)

            outbox.enqueueAppOnlyEntries()

            val seqs = mutableListOf<Long>()
            var item: QueuedMalChange? = outbox.oldest()
            while (item != null) {
                seqs += item.seq
                outbox.drop(item)
                item = outbox.oldest()
            }
            assertEquals(3, seqs.toSet().size)
        }

    @Test
    fun anAnimeWithAQueuedChangeIsPending() =
        runTest {
            outbox.enqueue(MalChange.Upsert(bebop, WatchStatus.WATCHING, 5, 7))

            assertTrue(outbox.isPending(bebop))
        }

    @Test
    fun aQueuedRemoveIsPending() =
        runTest {
            outbox.enqueue(MalChange.Remove(bebop))

            assertTrue(outbox.isPending(bebop))
        }

    @Test
    fun anAnimeWithoutAQueuedChangeIsNotPending() =
        runTest {
            outbox.enqueue(MalChange.Upsert(bebop, WatchStatus.WATCHING, 5, 7))

            assertFalse(outbox.isPending(fmab))
        }

    @Test
    fun nothingIsPendingInAnEmptyQueue() =
        runTest {
            assertFalse(outbox.isPending(bebop))
        }

    @Test
    fun aSentChangeIsNoLongerPending() =
        runTest {
            library.saveEntry(bebop, WatchStatus.WATCHING, 5, 7)
            outbox.enqueue(MalChange.Upsert(bebop, WatchStatus.WATCHING, 5, 7))

            outbox.markSent(outbox.oldest()!!)

            assertFalse(outbox.isPending(bebop))
        }

    @Test
    fun aDroppedChangeIsNoLongerPending() =
        runTest {
            outbox.enqueue(MalChange.Upsert(bebop, WatchStatus.WATCHING, 5, 7))

            outbox.drop(outbox.oldest()!!)

            assertFalse(outbox.isPending(bebop))
        }

    @Test
    fun aFailedChangeIsStillPending() =
        runTest {
            outbox.enqueue(MalChange.Upsert(bebop, WatchStatus.WATCHING, 5, 7))
            outbox.markFailed(outbox.oldest()!!, MalSendError.SERVER)

            assertTrue(outbox.isPending(bebop))
        }

    @Test
    fun aClearedQueueHasNothingPending() =
        runTest {
            outbox.enqueue(MalChange.Upsert(bebop, WatchStatus.WATCHING, 5, 7))

            outbox.clear()

            assertFalse(outbox.isPending(bebop))
        }

    @Test
    fun aConflictedAnimeStaysPendingButIsNotOffered() =
        runTest {
            outbox.enqueue(MalChange.Upsert(bebop, WatchStatus.WATCHING, 5, 7))
            conflicts.saveConflict(bebop, EntryValues(WatchStatus.COMPLETED, 26, 9, removed = false))

            assertTrue(outbox.isPending(bebop))
            assertNull(outbox.oldest())
        }

    @Test
    fun oldestSkipsAChangeWhoseAnimeHasAConflictAndOffersTheNextOne() =
        runTest {
            outbox.enqueue(MalChange.Upsert(bebop, WatchStatus.WATCHING, 5, 7))
            outbox.enqueue(MalChange.Upsert(fmab, WatchStatus.COMPLETED, 64, 10))
            conflicts.saveConflict(bebop, EntryValues(WatchStatus.COMPLETED, 26, 9, removed = false))

            assertEquals(MalChange.Upsert(fmab, WatchStatus.COMPLETED, 64, 10), outbox.oldest()?.change)
        }

    @Test
    fun aConflictedRemoveIsSkippedToo() =
        runTest {
            outbox.enqueue(MalChange.Remove(bebop))
            outbox.enqueue(MalChange.Remove(fmab))
            conflicts.saveConflict(bebop, EntryValues(WatchStatus.WATCHING, 5, 7, removed = true))

            assertEquals(MalChange.Remove(fmab), outbox.oldest()?.change)
        }

    @Test
    fun drainingOffersOnlyTheChangesWithoutAConflict() =
        runTest {
            outbox.enqueue(MalChange.Upsert(steinsGate, WatchStatus.WATCHING, 1, null))
            outbox.enqueue(MalChange.Remove(bebop))
            outbox.enqueue(MalChange.Upsert(fmab, WatchStatus.COMPLETED, 64, 10))
            conflicts.saveConflict(bebop, EntryValues(WatchStatus.COMPLETED, 26, 9, removed = false))

            assertEquals(
                listOf(
                    MalChange.Upsert(steinsGate, WatchStatus.WATCHING, 1, null),
                    MalChange.Upsert(fmab, WatchStatus.COMPLETED, 64, 10),
                ),
                drain(),
            )
        }

    @Test
    fun resolvingTheConflictOffersTheChangeAgain() =
        runTest {
            outbox.enqueue(MalChange.Upsert(bebop, WatchStatus.WATCHING, 5, 7))
            conflicts.saveConflict(bebop, EntryValues(WatchStatus.COMPLETED, 26, 9, removed = false))
            assertNull(outbox.oldest())

            conflicts.deleteConflict(bebop)

            assertEquals(MalChange.Upsert(bebop, WatchStatus.WATCHING, 5, 7), outbox.oldest()?.change)
        }

    @Test
    fun aConflictedChangeIsStillCountedAsPending() =
        runTest {
            outbox.enqueue(MalChange.Upsert(bebop, WatchStatus.WATCHING, 5, 7))
            conflicts.saveConflict(bebop, EntryValues(WatchStatus.COMPLETED, 26, 9, removed = false))

            assertEquals(1, status().pendingCount)
        }
}
