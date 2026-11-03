package com.greenzean.animekmp.shared.conflict

import com.greenzean.animekmp.shared.auth.FakeSyncScheduler
import com.greenzean.animekmp.shared.conflict.domain.ConflictSide
import com.greenzean.animekmp.shared.conflict.domain.ResolveAllConflictsUseCase
import com.greenzean.animekmp.shared.library.domain.Anime
import com.greenzean.animekmp.shared.library.domain.SyncState
import com.greenzean.animekmp.shared.library.domain.WatchStatus
import com.greenzean.animekmp.shared.library.presentation.FakeLibraryLocalDataSource
import com.greenzean.animekmp.shared.library.presentation.SavedEntry
import com.greenzean.animekmp.shared.mal.MalOutboxHarness
import com.greenzean.animekmp.shared.mal.domain.MalChange
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResolveAllConflictsUseCaseTest {
    private val library = FakeLibraryLocalDataSource()
    private val conflicts = FakeConflictLocalDataSource()
    private val scheduler = FakeSyncScheduler()
    private val mal = MalOutboxHarness()
    private val resolveAll = ResolveAllConflictsUseCase(conflicts, library, scheduler, mal.queue)

    @Test
    fun keepAllAppDeletesEveryConflictAndChangesNoEntry() =
        runTest {
            seedThree()

            resolveAll(ConflictSide.App)

            assertTrue(conflicts.current.isEmpty())
            assertTrue(library.savedEntries.isEmpty())
            assertTrue(library.removedMalIds.isEmpty())
            assertEquals(0, scheduler.requests)
        }

    @Test
    fun keepAllMalAppliesEveryMalSideAndRequestsExactlyOneSync() =
        runTest {
            seedThree()

            resolveAll(ConflictSide.Other)

            assertEquals(
                listOf(
                    SavedEntry(1, WatchStatus.COMPLETED, 26, 9),
                    SavedEntry(2, WatchStatus.DROPPED, 40, null),
                ),
                library.savedEntries,
            )
            assertEquals(listOf(3), library.removedMalIds)
            assertTrue(conflicts.current.isEmpty())
            assertEquals(1, scheduler.requests)
        }

    @Test
    fun keepAllMalDoesNotClampProgress() =
        runTest {
            seedThree()

            resolveAll(ConflictSide.Other)

            assertEquals(40, library.savedEntries.first { it.malId == 2 }.progress)
        }

    @Test
    fun keepAllAppQueuesEveryAppVersionForMal() =
        runTest {
            library.put(Anime(1, "Anime 1", episodes = 12), SyncState.SYNCED, WatchStatus.WATCHING, 5, 7)
            library.put(Anime(2, "Anime 2", episodes = 12), SyncState.SYNCED, WatchStatus.WATCHING, 5, 7)
            conflicts.put(conflict(malId = 1, app = values(WatchStatus.WATCHING, 5, 7)))
            conflicts.put(conflict(malId = 2, app = values(removed = true)))

            resolveAll(ConflictSide.App)

            assertEquals(
                listOf(MalChange.Upsert(1, WatchStatus.WATCHING, 5, 7), MalChange.Remove(2)),
                mal.outbox.enqueued,
            )
        }

    @Test
    fun keepAllMalQueuesEveryMalVersionForMal() =
        runTest {
            seedThree()

            resolveAll(ConflictSide.Other)

            assertEquals(
                listOf(
                    MalChange.Upsert(1, WatchStatus.COMPLETED, 26, 9),
                    MalChange.Upsert(2, WatchStatus.DROPPED, 40, null),
                    MalChange.Remove(3),
                ),
                mal.outbox.enqueued,
            )
            assertEquals(3, mal.scheduler.requests)
        }

    @Test
    fun keepAllMalReplacesTheChangesWaitingInTheOutbox() =
        runTest {
            seedThree()
            mal.outbox.enqueue(MalChange.Upsert(1, WatchStatus.WATCHING, 5, 7))

            resolveAll(ConflictSide.Other)

            assertEquals(
                setOf<MalChange>(
                    MalChange.Upsert(1, WatchStatus.COMPLETED, 26, 9),
                    MalChange.Upsert(2, WatchStatus.DROPPED, 40, null),
                    MalChange.Remove(3),
                ),
                mal.outbox.pending.toSet(),
            )
            assertEquals(3, mal.outbox.pending.size)
        }

    @Test
    fun keepAllMalQueuesNothingWhileMalIsNotConnected() =
        runTest {
            val offline = MalOutboxHarness(loggedIn = false)
            seedThree()

            ResolveAllConflictsUseCase(conflicts, library, scheduler, offline.queue)(ConflictSide.Other)

            assertTrue(offline.outbox.enqueued.isEmpty())
            assertTrue(conflicts.current.isEmpty())
            assertEquals(1, scheduler.requests)
        }

    @Test
    fun keepAllMalWithNoConflictsQueuesNothing() =
        runTest {
            resolveAll(ConflictSide.Other)

            assertTrue(mal.outbox.enqueued.isEmpty())
        }

    private fun seedThree() {
        listOf(1, 2, 3).forEach { id ->
            library.put(Anime(id, "Anime $id", episodes = 12), SyncState.SYNCED, WatchStatus.WATCHING, 5, 7)
        }
        conflicts.put(conflict(malId = 1, other = values(WatchStatus.COMPLETED, 26, 9)))
        conflicts.put(conflict(malId = 2, other = values(WatchStatus.DROPPED, 40, null)))
        conflicts.put(conflict(malId = 3, other = values(removed = true)))
    }
}
