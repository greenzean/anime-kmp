package com.greenzean.animekmp.shared.importer

import com.greenzean.animekmp.shared.conflict.FakeConflictLocalDataSource
import com.greenzean.animekmp.shared.conflict.conflict
import com.greenzean.animekmp.shared.conflict.values
import com.greenzean.animekmp.shared.importer.domain.MergeMalEntryUseCase
import com.greenzean.animekmp.shared.importer.domain.MergeOutcome
import com.greenzean.animekmp.shared.library.domain.Anime
import com.greenzean.animekmp.shared.library.domain.SyncState
import com.greenzean.animekmp.shared.library.domain.WatchStatus
import com.greenzean.animekmp.shared.library.presentation.FakeLibraryLocalDataSource
import com.greenzean.animekmp.shared.library.presentation.SavedEntry
import com.greenzean.animekmp.shared.mal.FakeMalOutbox
import com.greenzean.animekmp.shared.mal.domain.MalChange
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class MergeMalEntryUseCaseTest {
    private val local = FakeLibraryLocalDataSource()
    private val conflicts = FakeConflictLocalDataSource()
    private val onMal = FakeOnMalLocalDataSource()
    private val outbox = FakeMalOutbox()
    private val merge = MergeMalEntryUseCase(local, conflicts, onMal, outbox)

    private fun putLocal(
        status: WatchStatus = WatchStatus.COMPLETED,
        progress: Int = 26,
        score: Int? = 9,
        updatedAtMillis: Long = 0,
    ) = local.put(Anime(1, "Cowboy Bebop", episodes = 26), SyncState.SYNCED, status, progress, score, updatedAtMillis)

    private fun at(millis: Long) = Instant.fromEpochMilliseconds(millis)

    @Test
    fun missingLocallyIsAddedWithTheImage() =
        runTest {
            val outcome = merge(malEntry(), "https://img.example/1.jpg", markOnMal = false)

            assertEquals(MergeOutcome.Added, outcome)
            val anime = local.savedAnime.single()
            assertEquals("Cowboy Bebop", anime.title)
            assertEquals(26, anime.episodes)
            assertEquals("https://img.example/1.jpg", anime.imageUrl)
            assertEquals(listOf(SavedEntry(1, WatchStatus.COMPLETED, 26, 9)), local.savedEntries)
            assertTrue(conflicts.saved.isEmpty())
        }

    @Test
    fun addedWithoutAnImageKeepsItNull() =
        runTest {
            merge(malEntry(), null, markOnMal = false)

            assertNull(local.savedAnime.single().imageUrl)
        }

    @Test
    fun addingDeletesAStaleConflict() =
        runTest {
            conflicts.put(conflict(malId = 1))

            merge(malEntry(), null, markOnMal = false)

            assertTrue(conflicts.current.isEmpty())
        }

    @Test
    fun differingEntryStoresAConflictAndKeepsTheAppVersion() =
        runTest {
            putLocal(WatchStatus.WATCHING, 5, 7)

            val outcome = merge(malEntry(), null, markOnMal = false)

            assertEquals(MergeOutcome.Conflict, outcome)
            assertEquals(listOf(1 to values(WatchStatus.COMPLETED, 26, 9)), conflicts.saved)
            assertTrue(local.savedEntries.isEmpty())
            assertEquals(5, local.getEntry(1)!!.progress)
        }

    @Test
    fun removedLocallyStoresAConflictAndIsNotReAdded() =
        runTest {
            putLocal()
            local.remove(1)

            val outcome = merge(malEntry(), null, markOnMal = false)

            assertEquals(MergeOutcome.Conflict, outcome)
            assertNull(local.getEntry(1))
            assertTrue(local.savedEntries.isEmpty())
            assertEquals(1, conflicts.saved.size)
        }

    @Test
    fun sameEntryIsUnchangedAndDeletesAStaleConflict() =
        runTest {
            putLocal()
            conflicts.put(conflict(malId = 1, other = values(WatchStatus.DROPPED, 3, 1)))

            val outcome = merge(malEntry(), null, markOnMal = false)

            assertEquals(MergeOutcome.Unchanged, outcome)
            assertTrue(conflicts.current.isEmpty())
            assertTrue(conflicts.saved.isEmpty())
        }

    @Test
    fun differingScoreIsAConflict() =
        runTest {
            putLocal(score = 8)

            assertEquals(MergeOutcome.Conflict, merge(malEntry(), null, markOnMal = false))
        }

    @Test
    fun markOnMalMarksAddedConflictAndUnchangedEntries() =
        runTest {
            putLocal(WatchStatus.WATCHING, 5, 7)
            local.put(Anime(2, "Trigun"), SyncState.SYNCED, WatchStatus.COMPLETED, 26, 9)

            merge(malEntry(1), null, markOnMal = true)
            merge(malEntry(2, "Trigun"), null, markOnMal = true)
            merge(malEntry(3, "Monster"), null, markOnMal = true)

            assertEquals(listOf(1, 2, 3), onMal.marked)
        }

    @Test
    fun withoutMarkOnMalNothingIsMarked() =
        runTest {
            putLocal()

            merge(malEntry(1), null, markOnMal = false)
            merge(malEntry(2, "Trigun"), null, markOnMal = false)

            assertTrue(onMal.marked.isEmpty())
        }

    @Test
    fun newerMalOverwritesTheLocalValuesAndDeletesTheConflict() =
        runTest {
            putLocal(WatchStatus.WATCHING, 5, 7, updatedAtMillis = 1000)
            conflicts.put(conflict(malId = 1))

            val outcome = merge(malEntry(), null, markOnMal = false, malUpdatedAt = at(2000))

            assertEquals(MergeOutcome.TookMal, outcome)
            assertEquals(listOf(SavedEntry(1, WatchStatus.COMPLETED, 26, 9)), local.savedEntries)
            assertTrue(conflicts.current.isEmpty())
            assertTrue(conflicts.saved.isEmpty())
        }

    @Test
    fun newerLocalIsKeptAndReturnsTheUpsertToSend() =
        runTest {
            putLocal(WatchStatus.WATCHING, 5, 7, updatedAtMillis = 3000)

            val outcome = merge(malEntry(), null, markOnMal = false, malUpdatedAt = at(2000))

            assertEquals(MergeOutcome.KeptLocal(MalChange.Upsert(1, WatchStatus.WATCHING, 5, 7)), outcome)
            assertTrue(local.savedEntries.isEmpty())
            assertEquals(5, local.getEntry(1)!!.progress)
            assertTrue(conflicts.saved.isEmpty())
        }

    @Test
    fun equalTimestampsKeepTheLocalValues() =
        runTest {
            putLocal(WatchStatus.WATCHING, 5, 7, updatedAtMillis = 2000)

            val outcome = merge(malEntry(), null, markOnMal = false, malUpdatedAt = at(2000))

            assertEquals(MergeOutcome.KeptLocal(MalChange.Upsert(1, WatchStatus.WATCHING, 5, 7)), outcome)
            assertTrue(local.savedEntries.isEmpty())
        }

    @Test
    fun keepingLocalDeletesAStaleConflict() =
        runTest {
            putLocal(WatchStatus.WATCHING, 5, 7, updatedAtMillis = 3000)
            conflicts.put(conflict(malId = 1))

            merge(malEntry(), null, markOnMal = false, malUpdatedAt = at(2000))

            assertTrue(conflicts.current.isEmpty())
        }

    @Test
    fun withoutAMalTimestampTheConflictIsKeptEvenWhenLocalIsOlder() =
        runTest {
            putLocal(WatchStatus.WATCHING, 5, 7, updatedAtMillis = 1000)

            val outcome = merge(malEntry(), null, markOnMal = false, malUpdatedAt = null)

            assertEquals(MergeOutcome.Conflict, outcome)
            assertEquals(1, conflicts.saved.size)
            assertTrue(local.savedEntries.isEmpty())
        }

    @Test
    fun removedLocallyStillConflictsWhenMalIsNewer() =
        runTest {
            putLocal(updatedAtMillis = 1000)
            local.remove(1)

            val outcome = merge(malEntry(), null, markOnMal = false, malUpdatedAt = at(2000))

            assertEquals(MergeOutcome.Conflict, outcome)
            assertNull(local.getEntry(1))
            assertTrue(local.savedEntries.isEmpty())
            assertEquals(1, conflicts.saved.size)
        }

    @Test
    fun sameValuesAreUnchangedEvenWithATimestamp() =
        runTest {
            putLocal(updatedAtMillis = 1000)
            conflicts.put(conflict(malId = 1))

            val outcome = merge(malEntry(), null, markOnMal = false, malUpdatedAt = at(2000))

            assertEquals(MergeOutcome.Unchanged, outcome)
            assertTrue(conflicts.current.isEmpty())
            assertTrue(local.savedEntries.isEmpty())
        }

    @Test
    fun pendingChangedAndNewerMalIsAConflictAndLocalValuesStay() =
        runTest {
            putLocal(WatchStatus.WATCHING, 5, 7, updatedAtMillis = 1000)
            outbox.enqueue(MalChange.Upsert(1, WatchStatus.WATCHING, 5, 7))

            val outcome = merge(malEntry(), null, markOnMal = false, malUpdatedAt = at(2000), malChanged = true)

            assertEquals(MergeOutcome.Conflict, outcome)
            assertEquals(listOf(1 to values(WatchStatus.COMPLETED, 26, 9)), conflicts.saved)
            assertTrue(local.savedEntries.isEmpty())
            assertEquals(5, local.getEntry(1)!!.progress)
        }

    @Test
    fun pendingChangedAndNewerLocalIsAConflictWithoutASecondQueueRow() =
        runTest {
            putLocal(WatchStatus.WATCHING, 5, 7, updatedAtMillis = 3000)
            outbox.enqueue(MalChange.Upsert(1, WatchStatus.WATCHING, 5, 7))

            val outcome = merge(malEntry(), null, markOnMal = false, malUpdatedAt = at(2000), malChanged = true)

            assertEquals(MergeOutcome.Conflict, outcome)
            assertEquals(1, conflicts.saved.size)
            assertEquals(1, outbox.enqueued.size)
            assertEquals(1, outbox.pending.size)
            assertTrue(local.savedEntries.isEmpty())
        }

    @Test
    fun pendingAndMalUnchangedIsUnchangedAndLeavesAnExistingConflict() =
        runTest {
            putLocal(WatchStatus.WATCHING, 5, 7, updatedAtMillis = 1000)
            outbox.enqueue(MalChange.Upsert(1, WatchStatus.WATCHING, 5, 7))
            conflicts.put(conflict(malId = 1))

            val outcome = merge(malEntry(), null, markOnMal = false, malUpdatedAt = at(2000), malChanged = false)

            assertEquals(MergeOutcome.Unchanged, outcome)
            assertEquals(1, conflicts.current.size)
            assertTrue(conflicts.saved.isEmpty())
            assertTrue(local.savedEntries.isEmpty())
            assertEquals(5, local.getEntry(1)!!.progress)
        }

    @Test
    fun pendingAndMalUnchangedWithNewerLocalStaysUnchanged() =
        runTest {
            putLocal(WatchStatus.WATCHING, 5, 7, updatedAtMillis = 3000)
            outbox.enqueue(MalChange.Upsert(1, WatchStatus.WATCHING, 5, 7))

            val outcome = merge(malEntry(), null, markOnMal = false, malUpdatedAt = at(2000), malChanged = false)

            assertEquals(MergeOutcome.Unchanged, outcome)
            assertEquals(1, outbox.pending.size)
        }

    @Test
    fun notPendingNewerMalStillTakesMalWhateverMalChanged() =
        runTest {
            putLocal(WatchStatus.WATCHING, 5, 7, updatedAtMillis = 1000)

            val outcome = merge(malEntry(), null, markOnMal = false, malUpdatedAt = at(2000), malChanged = false)

            assertEquals(MergeOutcome.TookMal, outcome)
        }

    @Test
    fun notPendingNewerLocalStillKeepsLocalWhateverMalChanged() =
        runTest {
            putLocal(WatchStatus.WATCHING, 5, 7, updatedAtMillis = 3000)

            val outcome = merge(malEntry(), null, markOnMal = false, malUpdatedAt = at(2000), malChanged = true)

            assertEquals(MergeOutcome.KeptLocal(MalChange.Upsert(1, WatchStatus.WATCHING, 5, 7)), outcome)
        }

    @Test
    fun pendingWithoutAMalTimestampIsStillAConflict() =
        runTest {
            putLocal(WatchStatus.WATCHING, 5, 7, updatedAtMillis = 1000)
            outbox.enqueue(MalChange.Upsert(1, WatchStatus.WATCHING, 5, 7))

            val outcome = merge(malEntry(), null, markOnMal = false, malUpdatedAt = null, malChanged = false)

            assertEquals(MergeOutcome.Conflict, outcome)
        }
}
