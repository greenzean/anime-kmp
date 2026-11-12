package com.greenzean.animekmp.shared.mal

import com.greenzean.animekmp.shared.conflict.conflict
import com.greenzean.animekmp.shared.conflict.values
import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.importer.domain.ImportSummary
import com.greenzean.animekmp.shared.importer.malEntry
import com.greenzean.animekmp.shared.library.domain.Anime
import com.greenzean.animekmp.shared.library.domain.SyncState
import com.greenzean.animekmp.shared.library.domain.WatchStatus
import com.greenzean.animekmp.shared.library.presentation.SavedEntry
import com.greenzean.animekmp.shared.mal.domain.ImportMalListUseCase
import com.greenzean.animekmp.shared.mal.domain.MalApiError
import com.greenzean.animekmp.shared.mal.domain.MalChange
import com.greenzean.animekmp.shared.mal.domain.MalError
import com.greenzean.animekmp.shared.mal.domain.MalListItem
import com.greenzean.animekmp.shared.mal.domain.MalListPage
import com.greenzean.animekmp.shared.mal.domain.MalPullResult
import com.greenzean.animekmp.shared.mal.domain.PullMalChangesUseCase
import com.greenzean.animekmp.shared.mal.domain.toBase
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class ImportMalListUseCaseTest {
    private fun TestScope.harness(initialOffset: Int = 0) =
        MalImportHarness(
            this,
            initialOffset,
            object : Clock {
                override fun now(): Instant = Instant.fromEpochMilliseconds(testScheduler.currentTime)
            },
        )

    private fun summary(result: Result<ImportSummary, MalError>): ImportSummary = (result as Result.Success).data

    @Test
    fun emptyListCompletesWithNothingAddedAndNoSync() =
        runTest {
            val h = harness()

            val result = h.importList {}

            assertEquals(Result.Success(ImportSummary(0, 0, 0)), result)
            assertEquals(0, h.scheduler.requests)
            assertEquals(listOf(0 to IMPORT_PAGE_SIZE), h.api.calls)
        }

    @Test
    fun newEntriesAreAddedWithTheirImage() =
        runTest {
            val h = harness()
            h.api.results[0] = listPage(0, 3, hasNext = false)

            val result = h.importList {}

            assertEquals(ImportSummary(added = 3, conflicts = 0, invalid = 0), summary(result))
            assertEquals(listOf(1, 2, 3), h.local.savedAnime.map { it.malId })
            assertEquals(listOf("img-1", "img-2", "img-3"), h.local.savedAnime.map { it.imageUrl })
            assertEquals(3, h.local.savedEntries.size)
        }

    @Test
    fun everyImportedItemSavesItsMalValuesAsTheBase() =
        runTest {
            val h = harness()
            h.api.results[0] = listPage(0, 3, hasNext = false)

            h.importList {}

            assertEquals(listItems(0, 3).associate { it.entry.malId to it.entry.toBase() }, h.baseStore.values)
        }

    @Test
    fun itemsOnLaterPagesSaveTheirBaseToo() =
        runTest {
            val h = harness()
            h.api.results[0] = listPage(0, IMPORT_PAGE_SIZE, hasNext = true)
            h.api.results[IMPORT_PAGE_SIZE] = listPage(IMPORT_PAGE_SIZE, 2, hasNext = false)

            h.importList {}

            assertEquals(IMPORT_PAGE_SIZE + 2, h.baseStore.values.size)
            assertEquals(
                listItems(IMPORT_PAGE_SIZE, 2).last().entry.toBase(),
                h.baseStore.values[IMPORT_PAGE_SIZE + 2],
            )
        }

    @Test
    fun aConflictingItemStillSavesTheMalValuesAsTheBase() =
        runTest {
            val h = harness()
            h.local.put(Anime(1, "Title 1", episodes = 26), SyncState.SYNCED, WatchStatus.WATCHING, 5, 7)
            h.api.results[0] = listPage(0, 1, hasNext = false)

            h.importList {}

            assertEquals(listItems(0, 1).single().entry.toBase(), h.baseStore.values[1])
        }

    @Test
    fun aFailedPageSavesNoBaseForIt() =
        runTest {
            val h = harness()
            h.api.results[0] = Result.Error(MalApiError.SERVER)

            h.importList {}

            assertTrue(h.baseStore.values.isEmpty())
        }

    @Test
    fun entriesThatDifferBecomeConflictsAndKeepTheAppVersion() =
        runTest {
            val h = harness()
            h.local.put(Anime(1, "Title 1", episodes = 26), SyncState.SYNCED, WatchStatus.WATCHING, 5, 7)
            h.api.results[0] = listPage(0, 2, hasNext = false)

            val result = h.importList {}

            assertEquals(ImportSummary(added = 1, conflicts = 1, invalid = 0), summary(result))
            assertEquals(listOf(1 to values(WatchStatus.COMPLETED, 26, 9)), h.conflicts.saved)
            assertEquals(5, h.local.getEntry(1)!!.progress)
        }

    @Test
    fun entryRemovedInTheAppBecomesAConflict() =
        runTest {
            val h = harness()
            h.local.put(Anime(1, "Title 1", episodes = 26), SyncState.SYNCED, WatchStatus.COMPLETED, 26, 9)
            h.local.remove(1)
            h.api.results[0] = listPage(0, 1, hasNext = false)

            val result = h.importList {}

            assertEquals(ImportSummary(added = 0, conflicts = 1, invalid = 0), summary(result))
            assertTrue(h.local.savedEntries.isEmpty())
        }

    @Test
    fun equalEntriesAreNotCounted() =
        runTest {
            val h = harness()
            h.local.put(Anime(1, "Title 1", episodes = 26), SyncState.SYNCED, WatchStatus.COMPLETED, 26, 9)
            h.api.results[0] = listPage(0, 1, hasNext = false)

            assertEquals(ImportSummary(0, 0, 0), summary(h.importList {}))
        }

    @Test
    fun everyImportedEntryIsMarkedOnMal() =
        runTest {
            val h = harness()
            h.local.put(Anime(1, "Title 1", episodes = 26), SyncState.SYNCED, WatchStatus.WATCHING, 5, 7)
            h.local.put(Anime(2, "Title 2", episodes = 26), SyncState.SYNCED, WatchStatus.COMPLETED, 26, 9)
            h.api.results[0] = listPage(0, 3, hasNext = false)

            h.importList {}

            assertEquals(listOf(1, 2), h.onMal.marked)
            assertEquals(listOf(3), h.local.importedMarkedOnMal)
        }

    @Test
    fun pagesAreRequestedOneHundredAtATimeUntilThereIsNoNext() =
        runTest {
            val h = harness()
            h.api.results[0] = listPage(0, 100, hasNext = true)
            h.api.results[100] = listPage(100, 100, hasNext = true)
            h.api.results[200] = listPage(200, 5, hasNext = false)

            val result = h.importList {}

            assertEquals(205, summary(result).added)
            assertEquals(listOf(0, 100, 200), h.api.offsets)
            assertTrue(h.api.calls.all { it.second == 100 })
        }

    @Test
    fun offsetIsSavedAfterEachPageAndClearedOnCompletion() =
        runTest {
            val h = harness()
            h.api.results[0] = listPage(0, 100, hasNext = true)
            h.api.results[100] = listPage(100, 100, hasNext = true)
            h.api.results[200] = listPage(200, 5, hasNext = false)

            h.importList {}

            assertEquals(listOf(100, 200), h.store.saved.take(2))
            assertEquals(0, h.store.offset())
            assertTrue(h.store.clears >= 1)
        }

    @Test
    fun offsetIsSavedBeforeTheNextPageIsRequested() =
        runTest {
            val h = harness()
            h.api.results[0] = listPage(0, 100, hasNext = true)
            h.api.results[100] = Result.Error(MalApiError.NO_CONNECTION)

            h.importList {}

            assertEquals(100, h.store.offset())
        }

    @Test
    fun resumesFromTheStoredOffset() =
        runTest {
            val h = harness(initialOffset = 100)
            h.api.results[100] = listPage(100, 3, hasNext = false)

            val result = h.importList {}

            assertEquals(listOf(100), h.api.offsets)
            assertEquals(3, summary(result).added)
            assertEquals(listOf(101, 102, 103), h.local.savedAnime.map { it.malId })
            assertEquals(0, h.store.offset())
        }

    @Test
    fun aFailedRunKeepsTheOffsetAndTheNextRunResumesAndFinishes() =
        runTest {
            val h = harness()
            h.api.results[0] = listPage(0, 100, hasNext = true)
            h.api.results[100] = Result.Error(MalApiError.NO_CONNECTION)

            val first = h.importList {}

            assertEquals(Result.Error(MalError.NoConnection), first)
            assertEquals(100, h.store.offset())
            assertEquals(0, h.store.clears)

            h.api.calls.clear()
            h.api.results[100] = listPage(100, 2, hasNext = false)
            val second = h.importList {}

            assertEquals(listOf(100), h.api.offsets)
            assertEquals(2, summary(second).added)
            assertEquals(0, h.store.offset())
        }

    @Test
    fun throttledStopsAndKeepsTheOffset() =
        runTest {
            val h = harness()
            h.api.results[0] = listPage(0, 100, hasNext = true)
            h.api.results[100] = Result.Error(MalApiError.THROTTLED)
            h.api.results[200] = listPage(200, 1, hasNext = false)

            val result = h.importList {}

            assertIs<MalError.Throttled>((result as Result.Error).error)
            assertEquals(listOf(0, 100), h.api.offsets)
            assertEquals(100, h.store.offset())
        }

    @Test
    fun blockedStopsAndKeepsTheOffset() =
        runTest {
            val h = harness(initialOffset = 100)
            h.api.results[100] = Result.Error(MalApiError.FORBIDDEN)

            val result = h.importList {}

            assertIs<MalError.Blocked>((result as Result.Error).error)
            assertEquals(100, h.store.offset())
            assertEquals(0, h.store.clears)
        }

    @Test
    fun errorOnTheFirstPageKeepsTheStoredOffsetUntouched() =
        runTest {
            val h = harness(initialOffset = 200)
            h.api.results[200] = Result.Error(MalApiError.SERVER)

            val result = h.importList {}

            assertEquals(Result.Error(MalError.Server), result)
            assertEquals(200, h.store.offset())
            assertTrue(h.store.saved.isEmpty())
        }

    @Test
    fun syncIsRequestedOnceWhenSomethingWasAdded() =
        runTest {
            val h = harness()
            h.api.results[0] = listPage(0, 100, hasNext = true)
            h.api.results[100] = listPage(100, 100, hasNext = false)

            h.importList {}

            assertEquals(1, h.scheduler.requests)
        }

    @Test
    fun syncIsNotRequestedWhenOnlyConflictsOrNothingChanged() =
        runTest {
            val h = harness()
            h.local.put(Anime(1, "Title 1", episodes = 26), SyncState.SYNCED, WatchStatus.WATCHING, 5, 7)
            h.local.put(Anime(2, "Title 2", episodes = 26), SyncState.SYNCED, WatchStatus.COMPLETED, 26, 9)
            h.api.results[0] = listPage(0, 2, hasNext = false)

            h.importList {}

            assertEquals(0, h.scheduler.requests)
        }

    @Test
    fun progressReportsTheCountOfEntriesMergedSoFar() =
        runTest {
            val h = harness()
            h.api.results[0] = listPage(0, 100, hasNext = true)
            h.api.results[100] = listPage(100, 50, hasNext = false)
            val reported = mutableListOf<Int>()

            h.importList { reported += it }

            assertEquals(150, reported.last())
            assertEquals(reported.sorted(), reported)
            assertTrue(100 in reported)
        }

    @Test
    fun nullScoreAndEpisodesFromMalAreStored() =
        runTest {
            val h = harness()
            val entry = malEntry(7, "Fresh", null, WatchStatus.PLAN_TO_WATCH, 0, null)
            h.api.results[0] = Result.Success(MalListPage(listOf(MalListItem(entry, null)), hasNext = false))

            h.importList {}

            assertEquals(
                null,
                h.local.savedAnime
                    .single()
                    .episodes,
            )
            assertEquals(
                null,
                h.local.savedEntries
                    .single()
                    .score,
            )
            assertEquals(
                null,
                h.local.savedAnime
                    .single()
                    .imageUrl,
            )
        }

    @Test
    fun finishingTheImportQueuesTheAppOnlyEntriesAfterAServerSync() =
        runTest {
            val h = harness()
            h.api.results[0] = listPage(0, 2, hasNext = false)

            h.importList {}

            assertEquals(listOf("sync", "enqueueAppOnly", "requestSend"), h.outboxHarness.log)
        }

    @Test
    fun aFailedImportQueuesNothing() =
        runTest {
            val h = harness()
            h.api.results[0] = Result.Error(MalApiError.NO_CONNECTION)

            h.importList {}

            assertEquals(0, h.outboxHarness.outbox.appOnlyEnqueues)
        }

    @Test
    fun importedEntriesAreNeverQueuedIndividually() =
        runTest {
            val h = harness()
            h.api.results[0] = listPage(0, 3, hasNext = false)

            h.importList {}

            assertTrue(
                h.outboxHarness.outbox.enqueued
                    .isEmpty(),
            )
        }

    @Test
    fun finishingTheImportSetsLastPulledAtToNow() =
        runTest {
            val h = harness()
            h.api.results[0] = listPage(0, 2, hasNext = false)

            h.importList {}

            assertEquals(listOf(Instant.fromEpochMilliseconds(testScheduler.currentTime)), h.pullStore.saved)
        }

    @Test
    fun finishingAnEmptyImportSetsLastPulledAt() =
        runTest {
            val h = harness()

            h.importList {}

            assertEquals(1, h.pullStore.saved.size)
        }

    @Test
    fun aFailedImportDoesNotSetLastPulledAt() =
        runTest {
            val h = harness()
            h.api.results[0] = listPage(0, 100, hasNext = true)
            h.api.results[100] = Result.Error(MalApiError.NO_CONNECTION)

            h.importList {}

            assertTrue(h.pullStore.saved.isEmpty())
            assertNull(h.pullStore.lastPulledAt())
        }

    private fun TestScope.localEntry(
        h: MalImportHarness,
        updatedAtMillis: Long,
    ) = h.local.put(
        Anime(1, "Title 1", episodes = 26),
        SyncState.SYNCED,
        WatchStatus.WATCHING,
        5,
        7,
        updatedAtMillis,
    )

    private fun TestScope.malItem(updatedAt: Instant?) =
        Result.Success(
            MalListPage(
                listOf(MalListItem(malEntry(malId = 1, title = "Title 1"), "img-1", updatedAt)),
                hasNext = false,
            ),
        )

    @Test
    fun newerMalOverwritesLocalWithoutAConflictAndRequestsAServerSync() =
        runTest {
            val h = harness()
            localEntry(h, 1000)
            h.api.results[0] = malItem(Instant.fromEpochMilliseconds(2000))

            val result = h.importList {}

            assertEquals(ImportSummary(added = 0, conflicts = 0, invalid = 0), summary(result))
            assertEquals(listOf(SavedEntry(1, WatchStatus.COMPLETED, 26, 9)), h.local.savedEntries)
            assertTrue(h.conflicts.saved.isEmpty())
            assertTrue(
                h.outboxHarness.outbox.enqueued
                    .isEmpty(),
            )
            assertEquals(1, h.scheduler.requests)
        }

    @Test
    fun onTheFirstImportNewerLocalStaysAndItsUpsertIsQueuedForMal() =
        runTest {
            val h = harness()
            localEntry(h, 3000)
            h.api.results[0] = malItem(Instant.fromEpochMilliseconds(2000))

            val result = h.importList {}

            assertEquals(ImportSummary(added = 0, conflicts = 0, invalid = 0), summary(result))
            assertTrue(h.local.savedEntries.isEmpty())
            assertTrue(h.conflicts.saved.isEmpty())
            assertEquals(
                listOf<MalChange>(MalChange.Upsert(1, WatchStatus.WATCHING, 5, 7)),
                h.outboxHarness.outbox.enqueued,
            )
            assertEquals(0, h.scheduler.requests)
        }

    private fun TestScope.pullOf(h: MalImportHarness) =
        PullMalChangesUseCase(
            h.api,
            h.throttle,
            h.outboxHarness.auth,
            h.local,
            h.conflicts,
            h.onMal,
            h.outboxHarness.outbox,
            h.pullStore,
            h.store,
            h.scheduler,
            h.baseStore,
            object : Clock {
                override fun now(): Instant = Instant.fromEpochMilliseconds(testScheduler.currentTime)
            },
        )

    @Test
    fun aFailedFirstImportThenAPullIsSkippedAndTheNextImportIsStillTheFirstImport() =
        runTest {
            val h = harness()
            localEntry(h, 3000)
            h.api.results[0] = Result.Error(MalApiError.NO_CONNECTION)

            assertIs<Result.Error<*>>(h.importList {})
            val pulled = pullOf(h)()

            assertEquals(MalPullResult.SKIPPED, pulled)
            assertEquals(1, h.api.calls.size)
            assertNull(h.pullStore.lastPulledAt())
            assertTrue(
                h.outboxHarness.outbox.enqueued
                    .isEmpty(),
            )

            h.api.results[0] = malItem(Instant.fromEpochMilliseconds(2000))
            h.importList {}

            assertEquals(
                listOf<MalChange>(MalChange.Upsert(1, WatchStatus.WATCHING, 5, 7)),
                h.outboxHarness.outbox.enqueued,
            )
            assertEquals(Instant.fromEpochMilliseconds(testScheduler.currentTime), h.pullStore.lastPulledAt())
        }

    private val completedImportAt = Instant.fromEpochMilliseconds(500)

    @Test
    fun aReimportDoesNotQueueAKeptLocalValueForAnEntryThatIsNotPending() =
        runTest {
            val h = harness()
            h.pullStore.setLastPulledAt(completedImportAt)
            localEntry(h, 3000)
            h.api.results[0] = malItem(Instant.fromEpochMilliseconds(2000))

            val result = h.importList {}

            assertEquals(ImportSummary(added = 0, conflicts = 0, invalid = 0), summary(result))
            assertTrue(h.local.savedEntries.isEmpty())
            assertTrue(h.conflicts.saved.isEmpty())
            assertTrue(
                h.outboxHarness.outbox.enqueued
                    .isEmpty(),
            )
            assertEquals(0, h.scheduler.requests)
        }

    @Test
    fun aReimportKeepsTheLocalValueWhenTimestampsAreEqual() =
        runTest {
            val h = harness()
            h.pullStore.setLastPulledAt(completedImportAt)
            localEntry(h, 2000)
            h.api.results[0] = malItem(Instant.fromEpochMilliseconds(2000))

            h.importList {}

            assertTrue(h.local.savedEntries.isEmpty())
            assertTrue(
                h.outboxHarness.outbox.enqueued
                    .isEmpty(),
            )
        }

    @Test
    fun aReimportStillAppliesANewerMalValue() =
        runTest {
            val h = harness()
            h.pullStore.setLastPulledAt(completedImportAt)
            localEntry(h, 1000)
            h.api.results[0] = malItem(Instant.fromEpochMilliseconds(2000))

            val result = h.importList {}

            assertEquals(ImportSummary(added = 0, conflicts = 0, invalid = 0), summary(result))
            assertEquals(listOf(SavedEntry(1, WatchStatus.COMPLETED, 26, 9)), h.local.savedEntries)
            assertEquals(1, h.scheduler.requests)
        }

    @Test
    fun aReimportStillAppliesThePendingConflictRule() =
        runTest {
            val h = harness()
            h.pullStore.setLastPulledAt(completedImportAt)
            pendingLocalNewerThanMal(h)

            val result = h.importList {}

            assertEquals(ImportSummary(added = 0, conflicts = 1, invalid = 0), summary(result))
            assertEquals(1, h.conflicts.saved.size)
            assertTrue(h.local.savedEntries.isEmpty())
            assertEquals(1, h.outboxHarness.outbox.enqueued.size)
        }

    @Test
    fun aReimportStillAddsNewEntries() =
        runTest {
            val h = harness()
            h.pullStore.setLastPulledAt(completedImportAt)
            h.api.results[0] = listPage(0, 2, hasNext = false)

            val result = h.importList {}

            assertEquals(2, summary(result).added)
        }

    @Test
    fun anInterruptedFirstImportResumedStillQueuesAKeptLocalValue() =
        runTest {
            val h = harness(initialOffset = 100)
            assertNull(h.pullStore.lastPulledAt())
            localEntry(h, 3000)
            h.api.results[100] = malItem(Instant.fromEpochMilliseconds(2000))

            h.importList {}

            assertEquals(
                listOf<MalChange>(MalChange.Upsert(1, WatchStatus.WATCHING, 5, 7)),
                h.outboxHarness.outbox.enqueued,
            )
        }

    @Test
    fun onTheFirstImportEqualTimestampsKeepLocalAndQueueTheUpsert() =
        runTest {
            val h = harness()
            localEntry(h, 2000)
            h.api.results[0] = malItem(Instant.fromEpochMilliseconds(2000))

            h.importList {}

            assertTrue(h.local.savedEntries.isEmpty())
            assertEquals(
                listOf<MalChange>(MalChange.Upsert(1, WatchStatus.WATCHING, 5, 7)),
                h.outboxHarness.outbox.enqueued,
            )
        }

    @Test
    fun keptLocalIsNotQueuedWhenMalIsNotConnected() =
        runTest {
            val h = harness()
            val loggedOut = MalOutboxHarness(loggedIn = false)
            val importList =
                ImportMalListUseCase(
                    h.api,
                    h.throttle,
                    h.merge,
                    h.store,
                    h.scheduler,
                    loggedOut.queueAppOnly,
                    loggedOut.queue,
                    pullStore = h.pullStore,
                    baseStore = h.baseStore,
                )
            localEntry(h, 3000)
            h.api.results[0] = malItem(Instant.fromEpochMilliseconds(2000))

            importList {}

            assertTrue(loggedOut.outbox.enqueued.isEmpty())
            assertEquals(0, loggedOut.scheduler.requests)
            assertTrue(h.local.savedEntries.isEmpty())
        }

    @Test
    fun resolvingByTimestampDeletesAStaleConflict() =
        runTest {
            val h = harness()
            localEntry(h, 3000)
            h.conflicts.put(conflict(malId = 1))
            h.api.results[0] = malItem(Instant.fromEpochMilliseconds(2000))

            h.importList {}

            assertTrue(h.conflicts.current.isEmpty())
        }

    @Test
    fun entriesWithoutAMalTimestampStillConflict() =
        runTest {
            val h = harness()
            localEntry(h, 1000)
            h.api.results[0] = malItem(null)

            val result = h.importList {}

            assertEquals(ImportSummary(added = 0, conflicts = 1, invalid = 0), summary(result))
            assertEquals(1, h.conflicts.saved.size)
            assertTrue(h.local.savedEntries.isEmpty())
            assertTrue(
                h.outboxHarness.outbox.enqueued
                    .isEmpty(),
            )
        }

    private suspend fun TestScope.pendingLocalNewerThanMal(h: MalImportHarness) {
        localEntry(h, 3000)
        h.outboxHarness.outbox.enqueue(MalChange.Upsert(1, WatchStatus.WATCHING, 5, 7))
        h.api.results[0] = malItem(Instant.fromEpochMilliseconds(2000))
    }

    @Test
    fun pendingEntryWhoseMalValuesEqualTheBaseIsNotAConflict() =
        runTest {
            val h = harness()
            pendingLocalNewerThanMal(h)
            h.baseStore.values[1] = malEntry(malId = 1, title = "Title 1").toBase()

            val result = h.importList {}

            assertEquals(ImportSummary(added = 0, conflicts = 0, invalid = 0), summary(result))
            assertTrue(h.conflicts.saved.isEmpty())
            assertEquals(1, h.outboxHarness.outbox.enqueued.size)
        }

    @Test
    fun pendingEntryWithoutABaseIsAConflict() =
        runTest {
            val h = harness()
            pendingLocalNewerThanMal(h)

            val result = h.importList {}

            assertEquals(ImportSummary(added = 0, conflicts = 1, invalid = 0), summary(result))
            assertEquals(1, h.conflicts.saved.size)
            assertEquals(1, h.outboxHarness.outbox.enqueued.size)
        }

    @Test
    fun pendingEntryWhoseMalValuesDifferFromTheBaseIsAConflict() =
        runTest {
            val h = harness()
            pendingLocalNewerThanMal(h)
            h.baseStore.values[1] = malEntry(malId = 1, title = "Title 1", progress = 3).toBase()

            val result = h.importList {}

            assertEquals(ImportSummary(added = 0, conflicts = 1, invalid = 0), summary(result))
            assertEquals(1, h.conflicts.saved.size)
        }

    @Test
    fun anExistingConflictSurvivesWhenMalHasNotChangedSinceTheBase() =
        runTest {
            val h = harness()
            pendingLocalNewerThanMal(h)
            h.baseStore.values[1] = malEntry(malId = 1, title = "Title 1").toBase()
            h.conflicts.put(conflict(malId = 1))

            h.importList {}

            assertEquals(1, h.conflicts.current.size)
        }
}
