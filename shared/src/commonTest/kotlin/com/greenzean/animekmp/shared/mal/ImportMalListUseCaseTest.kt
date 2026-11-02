package com.greenzean.animekmp.shared.mal

import com.greenzean.animekmp.shared.conflict.values
import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.importer.domain.ImportSummary
import com.greenzean.animekmp.shared.importer.malEntry
import com.greenzean.animekmp.shared.library.domain.Anime
import com.greenzean.animekmp.shared.library.domain.SyncState
import com.greenzean.animekmp.shared.library.domain.WatchStatus
import com.greenzean.animekmp.shared.mal.domain.MalApiError
import com.greenzean.animekmp.shared.mal.domain.MalError
import com.greenzean.animekmp.shared.mal.domain.MalListItem
import com.greenzean.animekmp.shared.mal.domain.MalListPage
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
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

            assertEquals(listOf(1, 2, 3), h.onMal.marked)
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
}
