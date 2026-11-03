package com.greenzean.animekmp.shared.library.domain

import com.greenzean.animekmp.shared.auth.FakeSyncScheduler
import com.greenzean.animekmp.shared.library.presentation.FakeLibraryLocalDataSource
import com.greenzean.animekmp.shared.library.presentation.SavedEntry
import com.greenzean.animekmp.shared.mal.MalOutboxHarness
import com.greenzean.animekmp.shared.mal.domain.MalChange
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EntryUseCasesTest {
    private val local = FakeLibraryLocalDataSource()
    private val scheduler = FakeSyncScheduler()
    private val mal = MalOutboxHarness()
    private val queue = mal.queue
    private val frieren = Anime(52991, "Sousou no Frieren", "Frieren", null, episodes = 28)

    @Test
    fun addSavesPlanToWatchWithNoProgressOrScore() =
        runTest {
            AddToLibraryUseCase(local, scheduler, queue)(frieren)

            assertEquals(listOf(frieren), local.savedAnime)
            assertEquals(SavedEntry(52991, WatchStatus.PLAN_TO_WATCH, 0, null), local.savedEntries.single())
            assertEquals(1, scheduler.requests)
        }

    @Test
    fun editAppliesTheRulesAndSavesTheEntry() =
        runTest {
            local.put(frieren, SyncState.SYNCED, WatchStatus.PLAN_TO_WATCH)

            EditEntryUseCase(local, scheduler, queue)(52991, EntryEdit.Progress(3))

            assertEquals(SavedEntry(52991, WatchStatus.WATCHING, 3, null), local.savedEntries.single())
            assertEquals(1, scheduler.requests)
        }

    @Test
    fun editKeepsTheScoreWhenChangingStatus() =
        runTest {
            local.put(frieren, SyncState.SYNCED, WatchStatus.WATCHING, progress = 4, score = 8)

            EditEntryUseCase(local, scheduler, queue)(52991, EntryEdit.Status(WatchStatus.ON_HOLD))

            assertEquals(SavedEntry(52991, WatchStatus.ON_HOLD, 4, 8), local.savedEntries.single())
        }

    @Test
    fun editStoresAScoreInRange() =
        runTest {
            local.put(frieren, SyncState.SYNCED, WatchStatus.WATCHING, progress = 4)

            EditEntryUseCase(local, scheduler, queue)(52991, EntryEdit.Score(9))

            assertEquals(9, local.savedEntries.single().score)
        }

    @Test
    fun editIgnoresAScoreOutOfRange() =
        runTest {
            local.put(frieren, SyncState.SYNCED, WatchStatus.WATCHING, progress = 4, score = 5)

            EditEntryUseCase(local, scheduler, queue)(52991, EntryEdit.Score(11))

            assertEquals(5, local.getEntry(52991)!!.score)
        }

    @Test
    fun incrementAddsOneEpisode() =
        runTest {
            local.put(frieren, SyncState.SYNCED, WatchStatus.WATCHING, progress = 27)

            EditEntryUseCase(local, scheduler, queue)(52991, EntryEdit.Increment)

            assertEquals(SavedEntry(52991, WatchStatus.COMPLETED, 28, null), local.savedEntries.single())
        }

    @Test
    fun editOfAnEntryThatIsNotInTheLibraryDoesNothing() =
        runTest {
            EditEntryUseCase(local, scheduler, queue)(52991, EntryEdit.Increment)

            assertEquals(emptyList(), local.savedEntries)
            assertEquals(0, scheduler.requests)
        }

    @Test
    fun removeDeletesTheEntryAndRequestsSync() =
        runTest {
            local.put(frieren, SyncState.SYNCED)

            RemoveFromLibraryUseCase(local, scheduler, queue)(52991)

            assertEquals(listOf(52991), local.removedMalIds)
            assertNull(local.getEntry(52991))
            assertEquals(1, scheduler.requests)
        }

    @Test
    fun restoreWritesTheSnapshotUnchanged() =
        runTest {
            local.put(frieren, SyncState.SYNCED, WatchStatus.WATCHING, progress = 9, score = 7)
            val snapshot = local.getEntry(52991)!!
            RemoveFromLibraryUseCase(local, scheduler, queue)(52991)

            RestoreEntryUseCase(local, scheduler, queue)(snapshot)

            assertEquals(SavedEntry(52991, WatchStatus.WATCHING, 9, 7), local.savedEntries.single())
            val restored = local.getEntry(52991)!!
            assertEquals(9, restored.progress)
            assertEquals(7, restored.score)
            assertEquals(2, scheduler.requests)
        }

    @Test
    fun addQueuesAPlanToWatchUpsertForMal() =
        runTest {
            AddToLibraryUseCase(local, scheduler, queue)(frieren)

            assertEquals(
                listOf<MalChange>(MalChange.Upsert(52991, WatchStatus.PLAN_TO_WATCH, 0, null)),
                mal.outbox.enqueued,
            )
            assertEquals(1, mal.scheduler.requests)
        }

    @Test
    fun editQueuesTheEditedValuesForMal() =
        runTest {
            local.put(frieren, SyncState.SYNCED, WatchStatus.WATCHING, progress = 4, score = 8)

            EditEntryUseCase(local, scheduler, queue)(52991, EntryEdit.Progress(5))

            assertEquals(
                listOf<MalChange>(MalChange.Upsert(52991, WatchStatus.WATCHING, 5, 8)),
                mal.outbox.enqueued,
            )
            assertEquals(1, mal.scheduler.requests)
        }

    @Test
    fun editThatCompletesTheShowQueuesTheCompletedStatus() =
        runTest {
            local.put(frieren, SyncState.SYNCED, WatchStatus.WATCHING, progress = 27)

            EditEntryUseCase(local, scheduler, queue)(52991, EntryEdit.Increment)

            assertEquals(
                listOf<MalChange>(MalChange.Upsert(52991, WatchStatus.COMPLETED, 28, null)),
                mal.outbox.enqueued,
            )
        }

    @Test
    fun editOfAnEntryThatIsNotInTheLibraryQueuesNothing() =
        runTest {
            EditEntryUseCase(local, scheduler, queue)(52991, EntryEdit.Increment)

            assertTrue(mal.outbox.enqueued.isEmpty())
            assertEquals(0, mal.scheduler.requests)
        }

    @Test
    fun removeQueuesARemoveForMal() =
        runTest {
            local.put(frieren, SyncState.SYNCED, WatchStatus.WATCHING, progress = 9)

            RemoveFromLibraryUseCase(local, scheduler, queue)(52991)

            assertEquals(listOf<MalChange>(MalChange.Remove(52991)), mal.outbox.enqueued)
            assertEquals(1, mal.scheduler.requests)
        }

    @Test
    fun restoreQueuesTheSnapshotUpsertReplacingTheRemove() =
        runTest {
            local.put(frieren, SyncState.SYNCED, WatchStatus.WATCHING, progress = 9, score = 7)
            val snapshot = local.getEntry(52991)!!
            RemoveFromLibraryUseCase(local, scheduler, queue)(52991)

            RestoreEntryUseCase(local, scheduler, queue)(snapshot)

            assertEquals(
                listOf(MalChange.Remove(52991), MalChange.Upsert(52991, WatchStatus.WATCHING, 9, 7)),
                mal.outbox.enqueued,
            )
            assertEquals(listOf<MalChange>(MalChange.Upsert(52991, WatchStatus.WATCHING, 9, 7)), mal.outbox.pending)
        }

    @Test
    fun nothingIsQueuedWhileMalIsNotConnected() =
        runTest {
            val offline = MalOutboxHarness(loggedIn = false)
            local.put(frieren, SyncState.SYNCED, WatchStatus.WATCHING, progress = 4)

            AddToLibraryUseCase(local, scheduler, offline.queue)(frieren)
            EditEntryUseCase(local, scheduler, offline.queue)(52991, EntryEdit.Increment)
            RemoveFromLibraryUseCase(local, scheduler, offline.queue)(52991)

            assertTrue(offline.outbox.enqueued.isEmpty())
            assertEquals(0, offline.scheduler.requests)
            assertEquals(3, scheduler.requests)
        }
}
