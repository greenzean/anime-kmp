package com.greenzean.animekmp.shared.mal

import com.greenzean.animekmp.shared.conflict.conflict
import com.greenzean.animekmp.shared.conflict.values
import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.library.domain.Anime
import com.greenzean.animekmp.shared.library.domain.SyncState
import com.greenzean.animekmp.shared.library.domain.WatchStatus
import com.greenzean.animekmp.shared.library.presentation.SavedEntry
import com.greenzean.animekmp.shared.mal.domain.MalApiError
import com.greenzean.animekmp.shared.mal.domain.MalBaseValues
import com.greenzean.animekmp.shared.mal.domain.MalChange
import com.greenzean.animekmp.shared.mal.domain.MalPullResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

private val LOCAL_EDIT_AT = PULL_NOW - 3.hours
private val MAL_NEWER = PULL_NOW - 1.hours
private val MAL_OLDER = PULL_NOW - 5.hours
private val PREVIOUS_PULL = PULL_NOW - 2.hours

@OptIn(ExperimentalCoroutinesApi::class)
class PullMalChangesUseCaseTest {
    private fun TestScope.harness(
        loggedIn: Boolean = true,
        initialOffset: Int = 0,
        lastPulledAt: Instant? = PREVIOUS_PULL,
    ) = MalPullHarness(
        backgroundScope + UnconfinedTestDispatcher(testScheduler),
        loggedIn,
        initialOffset,
        lastPulledAt,
    )

    private fun MalPullHarness.seed(
        malId: Int,
        status: WatchStatus = WatchStatus.WATCHING,
        progress: Int = 5,
        score: Int? = 7,
        updatedAt: Instant = LOCAL_EDIT_AT,
        onMalToo: Boolean = true,
    ) {
        library.put(
            Anime(malId, "Title $malId", episodes = 26),
            SyncState.SYNCED,
            status,
            progress,
            score,
            updatedAt.toEpochMilliseconds(),
        )
        if (onMalToo) onMal.active = onMal.active + malId
    }

    @Test
    fun anEntryMissingLocallyIsAddedAndMarkedOnMal() =
        runTest {
            val h = harness()
            h.api.results[0] = pullPage(pullItem(7, WatchStatus.WATCHING, 3, 8, MAL_NEWER))

            val result = h.pull()

            assertEquals(MalPullResult.DONE, result)
            assertEquals(listOf(7), h.library.savedAnime.map { it.malId })
            assertEquals(listOf(SavedEntry(7, WatchStatus.WATCHING, 3, 8)), h.library.savedEntries)
            assertEquals(listOf(7), h.onMal.marked)
            assertTrue(h.conflicts.current.isEmpty())
        }

    @Test
    fun anAddedEntryIsNotQueuedBackToMal() =
        runTest {
            val h = harness()
            h.api.results[0] = pullPage(pullItem(7))

            h.pull()

            assertTrue(h.outbox.enqueued.isEmpty())
        }

    @Test
    fun anEntryAddedWithoutAMalTimestampIsStillAdded() =
        runTest {
            val h = harness()
            h.api.results[0] = pullPage(pullItem(7, updatedAt = null))

            h.pull()

            assertEquals(listOf(7), h.library.savedEntries.map { it.malId })
        }

    @Test
    fun sameValuesDeleteAnExistingConflictAndChangeNothing() =
        runTest {
            val h = harness()
            h.seed(1, WatchStatus.COMPLETED, 26, 9)
            h.conflicts.put(conflict(malId = 1))
            h.api.results[0] = pullPage(pullItem(1, WatchStatus.COMPLETED, 26, 9, MAL_NEWER))

            h.pull()

            assertTrue(h.conflicts.current.isEmpty())
            assertTrue(1 in h.conflicts.deleted)
            assertTrue(h.library.savedEntries.isEmpty())
            assertTrue(h.library.removedMalIds.isEmpty())
            assertEquals(0, h.syncScheduler.requests)
        }

    @Test
    fun differentValuesWithMalNewerAndNothingPendingAreAppliedLocally() =
        runTest {
            val h = harness()
            h.seed(1)
            h.api.results[0] = pullPage(pullItem(1, WatchStatus.COMPLETED, 26, 9, MAL_NEWER))

            h.pull()

            assertEquals(listOf(SavedEntry(1, WatchStatus.COMPLETED, 26, 9)), h.library.savedEntries)
            assertTrue(h.conflicts.current.isEmpty())
        }

    @Test
    fun anAppliedMalChangeRequestsAServerSyncAndIsNotQueuedBackToMal() =
        runTest {
            val h = harness()
            h.seed(1)
            h.api.results[0] = pullPage(pullItem(1, WatchStatus.COMPLETED, 26, 9, MAL_NEWER))

            h.pull()

            assertEquals(1, h.syncScheduler.requests)
            assertTrue(h.outbox.enqueued.isEmpty())
        }

    @Test
    fun aScoreRemovedOnMalIsAppliedAsNull() =
        runTest {
            val h = harness()
            h.seed(1, score = 7)
            h.api.results[0] = pullPage(pullItem(1, WatchStatus.WATCHING, 5, null, MAL_NEWER))

            h.pull()

            assertEquals(listOf(SavedEntry(1, WatchStatus.WATCHING, 5, null)), h.library.savedEntries)
        }

    @Test
    fun differentValuesWithMalOlderAreSkipped() =
        runTest {
            val h = harness()
            h.seed(1)
            h.api.results[0] = pullPage(pullItem(1, WatchStatus.COMPLETED, 26, 9, MAL_OLDER))

            val result = h.pull()

            assertEquals(MalPullResult.DONE, result)
            assertTrue(h.library.savedEntries.isEmpty())
            assertTrue(h.conflicts.current.isEmpty())
            assertEquals(0, h.syncScheduler.requests)
        }

    @Test
    fun differentValuesWithTheSameTimestampAreSkipped() =
        runTest {
            val h = harness()
            h.seed(1, updatedAt = LOCAL_EDIT_AT)
            h.api.results[0] = pullPage(pullItem(1, WatchStatus.COMPLETED, 26, 9, LOCAL_EDIT_AT))

            h.pull()

            assertTrue(h.library.savedEntries.isEmpty())
            assertTrue(h.conflicts.current.isEmpty())
        }

    @Test
    fun differentValuesWithoutAMalTimestampAreSkipped() =
        runTest {
            val h = harness()
            h.seed(1)
            h.api.results[0] = pullPage(pullItem(1, WatchStatus.COMPLETED, 26, 9, updatedAt = null))

            h.pull()

            assertTrue(h.library.savedEntries.isEmpty())
            assertTrue(h.conflicts.current.isEmpty())
            assertEquals(0, h.syncScheduler.requests)
        }

    @Test
    fun differentValuesWithAPendingChangeBecomeAConflictWithMalValues() =
        runTest {
            val h = harness()
            h.seed(1)
            h.outbox.enqueue(MalChange.Upsert(1, WatchStatus.WATCHING, 5, 7))
            h.api.results[0] = pullPage(pullItem(1, WatchStatus.COMPLETED, 26, 9, MAL_NEWER))

            h.pull()

            assertEquals(listOf(1 to values(WatchStatus.COMPLETED, 26, 9, removed = false)), h.conflicts.saved)
            assertTrue(h.library.savedEntries.isEmpty())
            assertEquals(0, h.syncScheduler.requests)
        }

    @Test
    fun aPendingChangeBecomesAConflictEvenWhenMalIsOlder() =
        runTest {
            val h = harness()
            h.seed(1)
            h.outbox.enqueue(MalChange.Upsert(1, WatchStatus.WATCHING, 5, 7))
            h.api.results[0] = pullPage(pullItem(1, WatchStatus.COMPLETED, 26, 9, MAL_OLDER))

            h.pull()

            assertEquals(listOf(1), h.conflicts.saved.map { it.first })
            assertTrue(h.library.savedEntries.isEmpty())
        }

    @Test
    fun aPendingChangeBecomesAConflictEvenWithoutAMalTimestamp() =
        runTest {
            val h = harness()
            h.seed(1)
            h.outbox.enqueue(MalChange.Upsert(1, WatchStatus.WATCHING, 5, 7))
            h.api.results[0] = pullPage(pullItem(1, WatchStatus.COMPLETED, 26, 9, updatedAt = null))

            h.pull()

            assertEquals(listOf(1), h.conflicts.saved.map { it.first })
        }

    @Test
    fun aPendingChangeWithTheSameValuesIsNotAConflict() =
        runTest {
            val h = harness()
            h.seed(1, WatchStatus.COMPLETED, 26, 9)
            h.outbox.enqueue(MalChange.Upsert(1, WatchStatus.COMPLETED, 26, 9))
            h.api.results[0] = pullPage(pullItem(1, WatchStatus.COMPLETED, 26, 9, MAL_NEWER))

            h.pull()

            assertTrue(h.conflicts.saved.isEmpty())
            assertTrue(h.library.savedEntries.isEmpty())
        }

    @Test
    fun onlyTheEntryWithAPendingChangeBecomesAConflict() =
        runTest {
            val h = harness()
            h.seed(1)
            h.seed(2)
            h.outbox.enqueue(MalChange.Upsert(1, WatchStatus.WATCHING, 5, 7))
            h.api.results[0] =
                pullPage(
                    pullItem(1, WatchStatus.COMPLETED, 26, 9, MAL_NEWER),
                    pullItem(2, WatchStatus.DROPPED, 2, null, MAL_NEWER),
                )

            h.pull()

            assertEquals(listOf(1), h.conflicts.saved.map { it.first })
            assertEquals(listOf(SavedEntry(2, WatchStatus.DROPPED, 2, null)), h.library.savedEntries)
        }

    @Test
    fun anActiveOnMalEntryNotOnMalIsRemovedAfterACompleteWalk() =
        runTest {
            val h = harness()
            h.seed(1, WatchStatus.COMPLETED, 26, 9)
            h.seed(2)
            h.api.results[0] = pullPage(pullItem(1, WatchStatus.COMPLETED, 26, 9, MAL_NEWER))

            val result = h.pull()

            assertEquals(MalPullResult.DONE, result)
            assertEquals(listOf(2), h.library.removedMalIds)
            assertEquals(1, h.syncScheduler.requests)
            assertTrue(h.outbox.enqueued.isEmpty())
        }

    @Test
    fun anEntryOnMalIsNotRemoved() =
        runTest {
            val h = harness()
            h.seed(1, WatchStatus.COMPLETED, 26, 9)
            h.api.results[0] = pullPage(pullItem(1, WatchStatus.COMPLETED, 26, 9, MAL_NEWER))

            h.pull()

            assertTrue(h.library.removedMalIds.isEmpty())
        }

    @Test
    fun anEntryThatWasNeverOnMalIsNotRemoved() =
        runTest {
            val h = harness()
            h.seed(3, onMalToo = false)
            h.api.results[0] = pullPage()

            h.pull()

            assertTrue(h.library.removedMalIds.isEmpty())
            assertTrue(h.conflicts.saved.isEmpty())
        }

    @Test
    fun entriesFoundOnALaterPageAreNotRemoved() =
        runTest {
            val h = harness()
            h.seed(1, WatchStatus.COMPLETED, 26, 9)
            h.seed(150, WatchStatus.COMPLETED, 26, 9)
            h.api.results[0] = pullPage(pullItem(1, WatchStatus.COMPLETED, 26, 9), hasNext = true)
            h.api.results[100] = pullPage(pullItem(150, WatchStatus.COMPLETED, 26, 9))

            h.pull()

            assertEquals(listOf(0, 100), h.api.offsets)
            assertTrue(h.library.removedMalIds.isEmpty())
        }

    @Test
    fun anActiveOnMalEntryNotOnMalWithAPendingChangeBecomesARemovedConflict() =
        runTest {
            val h = harness()
            h.seed(2)
            h.outbox.enqueue(MalChange.Upsert(2, WatchStatus.WATCHING, 6, 7))
            h.api.results[0] = pullPage()

            h.pull()

            val saved = h.conflicts.saved.single()
            assertEquals(2, saved.first)
            assertTrue(saved.second.removed)
            assertTrue(h.library.removedMalIds.isEmpty())
            assertEquals(0, h.syncScheduler.requests)
        }

    @Test
    fun aPageFailureReturnsRetryWithoutRemovalsAndKeepsLastPulledAt() =
        runTest {
            val h = harness(lastPulledAt = PREVIOUS_PULL)
            h.seed(1, WatchStatus.COMPLETED, 26, 9)
            h.seed(2)
            h.api.results[0] = pullPage(pullItem(1, WatchStatus.COMPLETED, 26, 9), hasNext = true)
            h.api.results[100] = Result.Error(MalApiError.NO_CONNECTION)

            val result = h.pull()

            assertEquals(MalPullResult.RETRY, result)
            assertTrue(h.library.removedMalIds.isEmpty())
            assertTrue(h.conflicts.saved.isEmpty())
            assertTrue(h.pullStore.saved.isEmpty())
            assertEquals(PREVIOUS_PULL, h.pullStore.lastPulledAt())
        }

    @Test
    fun aFailureOnTheFirstPageReturnsRetry() =
        runTest {
            val h = harness()
            h.api.results[0] = Result.Error(MalApiError.SERVER)

            assertEquals(MalPullResult.RETRY, h.pull())
            assertEquals(PREVIOUS_PULL, h.pullStore.lastPulledAt())
        }

    @Test
    fun aThrottledPageReturnsRetry() =
        runTest {
            val h = harness()
            h.seed(2)
            h.api.results[0] = Result.Error(MalApiError.THROTTLED)

            assertEquals(MalPullResult.RETRY, h.pull())
            assertTrue(h.library.removedMalIds.isEmpty())
        }

    @Test
    fun aBlockedPageReturnsRetry() =
        runTest {
            val h = harness()
            h.api.results[0] = Result.Error(MalApiError.FORBIDDEN)

            assertEquals(MalPullResult.RETRY, h.pull())
            assertTrue(h.pullStore.saved.isEmpty())
        }

    @Test
    fun successSavesTheCurrentTimeAsLastPulledAt() =
        runTest {
            val h = harness(lastPulledAt = PREVIOUS_PULL)
            h.api.results[0] = pullPage(pullItem(1))

            val result = h.pull()

            assertEquals(MalPullResult.DONE, result)
            assertEquals(listOf(PULL_NOW), h.pullStore.saved)
            assertEquals(PULL_NOW, h.pullStore.lastPulledAt())
        }

    @Test
    fun anEmptyMalListStillCompletesAndSavesLastPulledAt() =
        runTest {
            val h = harness()

            assertEquals(MalPullResult.DONE, h.pull())
            assertEquals(listOf(PULL_NOW), h.pullStore.saved)
        }

    @Test
    fun pagesAreRequestedOneHundredAtATimeFromTheStart() =
        runTest {
            val h = harness()
            h.api.results[0] = pullPage(pullItem(1), hasNext = true)
            h.api.results[100] = pullPage(pullItem(2), hasNext = true)
            h.api.results[200] = pullPage(pullItem(3))

            h.pull()

            assertEquals(listOf(0, 100, 200), h.api.offsets)
            assertTrue(h.api.calls.all { it.second == 100 })
            assertEquals(listOf(1, 2, 3), h.library.savedEntries.map { it.malId })
        }

    @Test
    fun loggedOutIsSkippedWithoutCallingMal() =
        runTest {
            val h = harness(loggedIn = false)
            h.seed(2)

            val result = h.pull()

            assertEquals(MalPullResult.SKIPPED, result)
            assertTrue(h.api.calls.isEmpty())
            assertTrue(h.pullStore.saved.isEmpty())
            assertTrue(h.library.removedMalIds.isEmpty())
        }

    @Test
    fun aPullBeforeAnyImportHasFinishedIsSkippedWithoutReadingMal() =
        runTest {
            val h = harness(lastPulledAt = null)
            h.seed(2)
            h.api.results[0] = pullPage(pullItem(1))

            val result = h.pull()

            assertEquals(MalPullResult.SKIPPED, result)
            assertTrue(h.api.calls.isEmpty())
            assertTrue(h.pullStore.saved.isEmpty())
            assertNull(h.pullStore.lastPulledAt())
            assertTrue(h.library.savedEntries.isEmpty())
            assertTrue(h.library.removedMalIds.isEmpty())
            assertTrue(h.baseStore.saved.isEmpty())
        }

    @Test
    fun aSavedImportOffsetIsSkippedWithoutCallingMal() =
        runTest {
            val h = harness(initialOffset = 100)
            h.seed(2)

            val result = h.pull()

            assertEquals(MalPullResult.SKIPPED, result)
            assertTrue(h.api.calls.isEmpty())
            assertTrue(h.pullStore.saved.isEmpty())
            assertTrue(h.library.removedMalIds.isEmpty())
        }

    @Test
    fun aSkippedPullKeepsTheImportOffset() =
        runTest {
            val h = harness(initialOffset = 200)

            h.pull()

            assertEquals(200, h.import.store.offset())
            assertEquals(0, h.import.store.clears)
        }

    @Test
    fun aPendingChangeStaysPendingWithoutAConflictWhenMalStillHasTheBase() =
        runTest {
            val h = harness()
            h.seed(1, WatchStatus.COMPLETED, 983, 9)
            val change = MalChange.Upsert(1, WatchStatus.COMPLETED, 983, 9)
            h.outbox.enqueue(change)
            h.baseStore.save(1, MalBaseValues(WatchStatus.COMPLETED, 982, 9))
            h.api.results[0] = pullPage(pullItem(1, WatchStatus.COMPLETED, 982, 9, MAL_NEWER))

            val result = h.pull()

            assertEquals(MalPullResult.DONE, result)
            assertTrue(h.conflicts.saved.isEmpty())
            assertTrue(h.conflicts.current.isEmpty())
            assertEquals(listOf<MalChange>(change), h.outbox.pending)
            assertEquals(listOf<MalChange>(change), h.outbox.enqueued)
            assertTrue(h.library.savedEntries.isEmpty())
            assertEquals(0, h.syncScheduler.requests)
        }

    @Test
    fun aPendingChangeBecomesAConflictWhenMalMovedAwayFromTheBase() =
        runTest {
            val h = harness()
            h.seed(1, WatchStatus.COMPLETED, 983, 9)
            h.outbox.enqueue(MalChange.Upsert(1, WatchStatus.COMPLETED, 983, 9))
            h.baseStore.save(1, MalBaseValues(WatchStatus.COMPLETED, 982, 9))
            h.api.results[0] = pullPage(pullItem(1, WatchStatus.COMPLETED, 985, 9, MAL_NEWER))

            h.pull()

            assertEquals(listOf(1 to values(WatchStatus.COMPLETED, 985, 9, removed = false)), h.conflicts.saved)
            assertTrue(h.library.savedEntries.isEmpty())
        }

    @Test
    fun aPendingChangeBecomesAConflictWhenThereIsNoBase() =
        runTest {
            val h = harness()
            h.seed(1)
            h.outbox.enqueue(MalChange.Upsert(1, WatchStatus.WATCHING, 5, 7))
            h.api.results[0] = pullPage(pullItem(1, WatchStatus.COMPLETED, 26, 9, MAL_NEWER))

            h.pull()

            assertEquals(listOf(1 to values(WatchStatus.COMPLETED, 26, 9, removed = false)), h.conflicts.saved)
        }

    @Test
    fun theBaseIsRefreshedWhenAPendingChangeStaysPending() =
        runTest {
            val h = harness()
            h.seed(1, WatchStatus.COMPLETED, 983, 9)
            h.outbox.enqueue(MalChange.Upsert(1, WatchStatus.COMPLETED, 983, 9))
            h.baseStore.save(1, MalBaseValues(WatchStatus.COMPLETED, 982, 9))
            h.api.results[0] = pullPage(pullItem(1, WatchStatus.COMPLETED, 982, 9, MAL_NEWER))

            h.pull()

            assertEquals(MalBaseValues(WatchStatus.COMPLETED, 982, 9), h.baseStore.values[1])
        }

    @Test
    fun theBaseIsSavedWithMalValuesWhenAConflictIsCreated() =
        runTest {
            val h = harness()
            h.seed(1)
            h.outbox.enqueue(MalChange.Upsert(1, WatchStatus.WATCHING, 5, 7))
            h.api.results[0] = pullPage(pullItem(1, WatchStatus.COMPLETED, 26, 9, MAL_NEWER))

            h.pull()

            assertEquals(MalBaseValues(WatchStatus.COMPLETED, 26, 9), h.baseStore.values[1])
        }

    @Test
    fun theBaseIsSavedWhenValuesAreTheSame() =
        runTest {
            val h = harness()
            h.seed(1, WatchStatus.COMPLETED, 26, 9)
            h.api.results[0] = pullPage(pullItem(1, WatchStatus.COMPLETED, 26, 9, MAL_NEWER))

            h.pull()

            assertEquals(MalBaseValues(WatchStatus.COMPLETED, 26, 9), h.baseStore.values[1])
        }

    @Test
    fun theBaseIsSavedWhenMalValuesAreAppliedLocally() =
        runTest {
            val h = harness()
            h.seed(1)
            h.api.results[0] = pullPage(pullItem(1, WatchStatus.COMPLETED, 26, null, MAL_NEWER))

            h.pull()

            assertEquals(MalBaseValues(WatchStatus.COMPLETED, 26, null), h.baseStore.values[1])
        }

    @Test
    fun theBaseIsSavedWhenMalIsOlderAndNothingIsApplied() =
        runTest {
            val h = harness()
            h.seed(1)
            h.api.results[0] = pullPage(pullItem(1, WatchStatus.COMPLETED, 26, 9, MAL_OLDER))

            h.pull()

            assertEquals(MalBaseValues(WatchStatus.COMPLETED, 26, 9), h.baseStore.values[1])
        }

    @Test
    fun theBaseIsSavedForAnEntryAddedLocally() =
        runTest {
            val h = harness()
            h.api.results[0] = pullPage(pullItem(7, WatchStatus.WATCHING, 3, 8, MAL_NEWER))

            h.pull()

            assertEquals(MalBaseValues(WatchStatus.WATCHING, 3, 8), h.baseStore.values[7])
        }

    @Test
    fun theBaseIsSavedForEveryItemOnEveryPage() =
        runTest {
            val h = harness()
            h.api.results[0] = pullPage(pullItem(1, progress = 1), hasNext = true)
            h.api.results[100] = pullPage(pullItem(2, progress = 2))

            h.pull()

            assertEquals(
                mapOf(
                    1 to MalBaseValues(WatchStatus.COMPLETED, 1, 9),
                    2 to MalBaseValues(WatchStatus.COMPLETED, 2, 9),
                ),
                h.baseStore.values,
            )
        }

    @Test
    fun theBaseOfAnEntryRemovedOnMalIsDeleted() =
        runTest {
            val h = harness()
            h.seed(2)
            h.baseStore.save(2, MalBaseValues(WatchStatus.WATCHING, 5, 7))
            h.api.results[0] = pullPage()

            h.pull()

            assertEquals(listOf(2), h.library.removedMalIds)
            assertNull(h.baseStore.get(2))
        }

    @Test
    fun theBaseOfAnEntryRemovedOnMalWithAPendingChangeIsDeletedToo() =
        runTest {
            val h = harness()
            h.seed(2)
            h.outbox.enqueue(MalChange.Upsert(2, WatchStatus.WATCHING, 6, 7))
            h.baseStore.save(2, MalBaseValues(WatchStatus.WATCHING, 5, 7))
            h.api.results[0] = pullPage()

            h.pull()

            assertTrue(
                h.conflicts.saved
                    .single()
                    .second.removed,
            )
            assertNull(h.baseStore.get(2))
        }

    @Test
    fun theBaseOfAnEntryStillOnMalIsKept() =
        runTest {
            val h = harness()
            h.seed(1, WatchStatus.COMPLETED, 26, 9)
            h.seed(2)
            h.api.results[0] = pullPage(pullItem(1, WatchStatus.COMPLETED, 26, 9, MAL_NEWER))

            h.pull()

            assertEquals(MalBaseValues(WatchStatus.COMPLETED, 26, 9), h.baseStore.values[1])
            assertEquals(listOf(2), h.baseStore.deleted)
        }

    @Test
    fun aPageFailureKeepsTheBasesSavedForEarlierItemsAndDeletesNone() =
        runTest {
            val h = harness()
            h.seed(2)
            h.baseStore.save(2, MalBaseValues(WatchStatus.WATCHING, 5, 7))
            h.api.results[0] = pullPage(pullItem(1, WatchStatus.COMPLETED, 26, 9), hasNext = true)
            h.api.results[100] = Result.Error(MalApiError.NO_CONNECTION)

            h.pull()

            assertEquals(MalBaseValues(WatchStatus.COMPLETED, 26, 9), h.baseStore.values[1])
            assertEquals(MalBaseValues(WatchStatus.WATCHING, 5, 7), h.baseStore.values[2])
            assertTrue(h.baseStore.deleted.isEmpty())
        }

    @Test
    fun aSkippedPullLeavesTheBasesUntouched() =
        runTest {
            val h = harness(loggedIn = false)
            h.baseStore.save(2, MalBaseValues(WatchStatus.WATCHING, 5, 7))
            h.api.results[0] = pullPage(pullItem(1))

            h.pull()

            assertEquals(1, h.baseStore.saved.size)
            assertTrue(h.baseStore.deleted.isEmpty())
        }
}
