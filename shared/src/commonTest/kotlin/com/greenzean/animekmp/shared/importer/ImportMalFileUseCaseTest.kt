package com.greenzean.animekmp.shared.importer

import com.greenzean.animekmp.shared.auth.FakeSyncScheduler
import com.greenzean.animekmp.shared.importer.domain.ImportMalFileUseCase
import com.greenzean.animekmp.shared.importer.domain.ImportSummary
import com.greenzean.animekmp.shared.importer.domain.MalExportEntry
import com.greenzean.animekmp.shared.importer.domain.MalExportFile
import com.greenzean.animekmp.shared.library.domain.Anime
import com.greenzean.animekmp.shared.library.domain.SyncState
import com.greenzean.animekmp.shared.library.domain.WatchStatus
import com.greenzean.animekmp.shared.library.presentation.FakeLibraryLocalDataSource
import com.greenzean.animekmp.shared.library.presentation.SavedEntry
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

fun malEntry(
    malId: Int = 1,
    title: String = "Cowboy Bebop",
    episodes: Int? = 26,
    status: WatchStatus = WatchStatus.COMPLETED,
    progress: Int = 26,
    score: Int? = 9,
) = MalExportEntry(malId, title, episodes, status, progress, score)

class ImportMalFileUseCaseTest {
    private val local = FakeLibraryLocalDataSource()
    private val scheduler = FakeSyncScheduler()
    private val useCase = ImportMalFileUseCase(local, scheduler)

    @Test
    fun newEntryIsAddedWithAnimeAndEntry() =
        runTest {
            val summary = useCase(MalExportFile(listOf(malEntry()), invalid = 0)) {}

            assertEquals(ImportSummary(added = 1, keptYours = 0, invalid = 0), summary)
            val anime = local.savedAnime.single()
            assertEquals(1, anime.malId)
            assertEquals("Cowboy Bebop", anime.title)
            assertEquals(26, anime.episodes)
            assertEquals(listOf(SavedEntry(1, WatchStatus.COMPLETED, 26, 9)), local.savedEntries)
        }

    @Test
    fun equalEntryIsSkippedAndNotCounted() =
        runTest {
            local.put(Anime(1, "Cowboy Bebop", episodes = 26), SyncState.SYNCED, WatchStatus.COMPLETED, 26, 9)

            val summary = useCase(MalExportFile(listOf(malEntry()), invalid = 0)) {}

            assertEquals(ImportSummary(0, 0, 0), summary)
            assertTrue(local.savedEntries.isEmpty())
            assertTrue(local.savedAnime.isEmpty())
        }

    @Test
    fun differingEntryKeepsAppVersionAndCountsKeptYours() =
        runTest {
            local.put(Anime(1, "Cowboy Bebop", episodes = 26), SyncState.SYNCED, WatchStatus.WATCHING, 5, 7)

            val summary = useCase(MalExportFile(listOf(malEntry()), invalid = 0)) {}

            assertEquals(ImportSummary(0, 1, 0), summary)
            assertTrue(local.savedEntries.isEmpty())
            val kept = local.getEntry(1)!!
            assertEquals(WatchStatus.WATCHING, kept.status)
            assertEquals(5, kept.progress)
            assertEquals(7, kept.score)
        }

    @Test
    fun removedInAppEntryCountsKeptYoursAndIsNotReAdded() =
        runTest {
            local.put(Anime(1, "Cowboy Bebop", episodes = 26), SyncState.SYNCED, WatchStatus.COMPLETED, 26, 9)
            local.remove(1)

            val summary = useCase(MalExportFile(listOf(malEntry()), invalid = 0)) {}

            assertEquals(ImportSummary(0, 1, 0), summary)
            assertTrue(local.savedEntries.isEmpty())
            assertEquals(null, local.getEntry(1))
        }

    @Test
    fun invalidCountIsPassedThroughFromFile() =
        runTest {
            val summary = useCase(MalExportFile(listOf(malEntry()), invalid = 4)) {}

            assertEquals(ImportSummary(1, 0, 4), summary)
        }

    @Test
    fun mixedFileCountsEachKind() =
        runTest {
            local.put(Anime(2, "Trigun"), SyncState.SYNCED, WatchStatus.DROPPED, 1)
            local.put(Anime(3, "Monster", episodes = 74), SyncState.SYNCED, WatchStatus.COMPLETED, 74, 10)

            val summary =
                useCase(
                    MalExportFile(
                        listOf(
                            malEntry(1),
                            malEntry(2, "Trigun", 26, WatchStatus.COMPLETED, 26, 8),
                            malEntry(3, "Monster", 74, WatchStatus.COMPLETED, 74, 10),
                        ),
                        invalid = 2,
                    ),
                ) {}

            assertEquals(ImportSummary(added = 1, keptYours = 1, invalid = 2), summary)
        }

    @Test
    fun nullEpisodesAndScoreAreSaved() =
        runTest {
            useCase(MalExportFile(listOf(malEntry(episodes = null, score = null)), invalid = 0)) {}

            assertEquals(null, local.savedAnime.single().episodes)
            assertEquals(null, local.savedEntries.single().score)
        }

    @Test
    fun progressReachesOne() =
        runTest {
            val progress = mutableListOf<Float>()

            useCase(MalExportFile(listOf(malEntry(1), malEntry(2), malEntry(3)), invalid = 0)) { progress += it }

            assertEquals(1f, progress.last())
            assertTrue(progress.all { it in 0f..1f })
            assertEquals(progress.sorted(), progress)
        }

    @Test
    fun emptyFileCompletesWithoutSync() =
        runTest {
            val summary = useCase(MalExportFile(emptyList(), invalid = 0)) {}

            assertEquals(ImportSummary(0, 0, 0), summary)
            assertEquals(0, scheduler.requests)
        }

    @Test
    fun syncRequestedOnceWhenSomethingWasAdded() =
        runTest {
            useCase(MalExportFile(listOf(malEntry(1), malEntry(2), malEntry(3)), invalid = 0)) {}

            assertEquals(1, scheduler.requests)
        }

    @Test
    fun syncNotRequestedWhenNothingWasAdded() =
        runTest {
            local.put(Anime(1, "Cowboy Bebop", episodes = 26), SyncState.SYNCED, WatchStatus.WATCHING, 5, 7)

            useCase(MalExportFile(listOf(malEntry()), invalid = 1)) {}

            assertEquals(0, scheduler.requests)
        }
}
