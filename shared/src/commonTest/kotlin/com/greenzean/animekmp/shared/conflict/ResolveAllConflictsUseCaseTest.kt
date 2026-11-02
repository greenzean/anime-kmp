package com.greenzean.animekmp.shared.conflict

import com.greenzean.animekmp.shared.auth.FakeSyncScheduler
import com.greenzean.animekmp.shared.conflict.domain.ConflictSide
import com.greenzean.animekmp.shared.conflict.domain.ResolveAllConflictsUseCase
import com.greenzean.animekmp.shared.library.domain.Anime
import com.greenzean.animekmp.shared.library.domain.SyncState
import com.greenzean.animekmp.shared.library.domain.WatchStatus
import com.greenzean.animekmp.shared.library.presentation.FakeLibraryLocalDataSource
import com.greenzean.animekmp.shared.library.presentation.SavedEntry
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResolveAllConflictsUseCaseTest {
    private val library = FakeLibraryLocalDataSource()
    private val conflicts = FakeConflictLocalDataSource()
    private val scheduler = FakeSyncScheduler()
    private val resolveAll = ResolveAllConflictsUseCase(conflicts, library, scheduler)

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

    private fun seedThree() {
        listOf(1, 2, 3).forEach { id ->
            library.put(Anime(id, "Anime $id", episodes = 12), SyncState.SYNCED, WatchStatus.WATCHING, 5, 7)
        }
        conflicts.put(conflict(malId = 1, other = values(WatchStatus.COMPLETED, 26, 9)))
        conflicts.put(conflict(malId = 2, other = values(WatchStatus.DROPPED, 40, null)))
        conflicts.put(conflict(malId = 3, other = values(removed = true)))
    }
}
