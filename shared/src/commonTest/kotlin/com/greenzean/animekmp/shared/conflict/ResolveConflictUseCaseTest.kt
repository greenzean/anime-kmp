package com.greenzean.animekmp.shared.conflict

import com.greenzean.animekmp.shared.auth.FakeSyncScheduler
import com.greenzean.animekmp.shared.conflict.domain.ConflictSide
import com.greenzean.animekmp.shared.conflict.domain.EntryValues
import com.greenzean.animekmp.shared.conflict.domain.ResolveConflictUseCase
import com.greenzean.animekmp.shared.library.domain.Anime
import com.greenzean.animekmp.shared.library.domain.SyncState
import com.greenzean.animekmp.shared.library.domain.WatchStatus
import com.greenzean.animekmp.shared.library.presentation.FakeLibraryLocalDataSource
import com.greenzean.animekmp.shared.library.presentation.SavedEntry
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ResolveConflictUseCaseTest {
    private val library = FakeLibraryLocalDataSource()
    private val conflicts = FakeConflictLocalDataSource()
    private val scheduler = FakeSyncScheduler()
    private val resolve = ResolveConflictUseCase(conflicts, library, scheduler)
    private val bebop = Anime(1, "Cowboy Bebop", episodes = 26)

    private fun seed(
        other: EntryValues = values(),
        malId: Int = 1,
    ) {
        library.put(Anime(malId, "Anime $malId", episodes = 12), SyncState.SYNCED, WatchStatus.WATCHING, 5, 7)
        conflicts.put(conflict(malId = malId, other = other))
    }

    @Test
    fun keepAppDeletesTheConflictAndChangesNothingElse() =
        runTest {
            library.put(bebop, SyncState.SYNCED, WatchStatus.WATCHING, 5, 7)
            conflicts.put(conflict())

            resolve(1, ConflictSide.App)

            assertTrue(conflicts.current.isEmpty())
            assertTrue(library.savedEntries.isEmpty())
            assertTrue(library.removedMalIds.isEmpty())
            assertEquals(0, scheduler.requests)
        }

    @Test
    fun keepMalSavesTheMalValuesDeletesTheConflictAndRequestsOneSync() =
        runTest {
            library.put(bebop, SyncState.SYNCED, WatchStatus.WATCHING, 5, 7)
            conflicts.put(conflict(other = values(WatchStatus.COMPLETED, 26, 9)))

            resolve(1, ConflictSide.Other)

            assertEquals(listOf(SavedEntry(1, WatchStatus.COMPLETED, 26, 9)), library.savedEntries)
            assertTrue(conflicts.current.isEmpty())
            assertEquals(1, scheduler.requests)
        }

    @Test
    fun keepMalWritesTheStoredValuesWithoutClamping() =
        runTest {
            library.put(Anime(1, "Cowboy Bebop", episodes = 12), SyncState.SYNCED, WatchStatus.WATCHING, 5, 7)
            conflicts.put(conflict(episodes = 12, other = values(WatchStatus.COMPLETED, 30, 9)))

            resolve(1, ConflictSide.Other)

            assertEquals(listOf(SavedEntry(1, WatchStatus.COMPLETED, 30, 9)), library.savedEntries)
        }

    @Test
    fun keepMalKeepsANullScore() =
        runTest {
            library.put(bebop, SyncState.SYNCED, WatchStatus.WATCHING, 5, 7)
            conflicts.put(conflict(other = values(WatchStatus.DROPPED, 3, null)))

            resolve(1, ConflictSide.Other)

            assertNull(library.savedEntries.single().score)
        }

    @Test
    fun keepMalRemovesTheEntryWhenTheOtherSideIsRemoved() =
        runTest {
            library.put(bebop, SyncState.SYNCED, WatchStatus.WATCHING, 5, 7)
            conflicts.put(conflict(other = values(removed = true)))

            resolve(1, ConflictSide.Other)

            assertEquals(listOf(1), library.removedMalIds)
            assertTrue(library.savedEntries.isEmpty())
            assertTrue(conflicts.current.isEmpty())
            assertEquals(1, scheduler.requests)
        }

    @Test
    fun keepMalReAddsAnEntryRemovedInTheApp() =
        runTest {
            library.put(bebop, SyncState.SYNCED, WatchStatus.WATCHING, 5, 7)
            library.remove(1)
            conflicts.put(conflict(app = values(removed = true)))

            resolve(1, ConflictSide.Other)

            assertEquals(WatchStatus.COMPLETED, library.getEntry(1)?.status)
            assertEquals(SyncState.PENDING, library.getEntry(1)?.syncState)
        }

    @Test
    fun resolvingOneConflictLeavesTheOthers() =
        runTest {
            seed(malId = 1)
            seed(malId = 2)

            resolve(1, ConflictSide.Other)

            assertEquals(listOf(2), conflicts.current.map { it.malId })
        }

    @Test
    fun keepMalForAnUnknownConflictDoesNothing() =
        runTest {
            resolve(99, ConflictSide.Other)

            assertTrue(library.savedEntries.isEmpty())
            assertTrue(library.removedMalIds.isEmpty())
        }
}
