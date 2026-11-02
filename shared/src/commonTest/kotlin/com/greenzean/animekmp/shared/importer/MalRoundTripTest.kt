package com.greenzean.animekmp.shared.importer

import com.greenzean.animekmp.shared.auth.FakeSyncScheduler
import com.greenzean.animekmp.shared.conflict.FakeConflictLocalDataSource
import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.export.domain.MalXmlWriter
import com.greenzean.animekmp.shared.export.exportEntry
import com.greenzean.animekmp.shared.importer.domain.ImportMalFileUseCase
import com.greenzean.animekmp.shared.importer.domain.ImportSummary
import com.greenzean.animekmp.shared.importer.domain.MalXmlParser
import com.greenzean.animekmp.shared.importer.domain.MergeMalEntryUseCase
import com.greenzean.animekmp.shared.library.domain.WatchStatus
import com.greenzean.animekmp.shared.library.presentation.FakeLibraryLocalDataSource
import com.greenzean.animekmp.shared.library.presentation.SavedEntry
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MalRoundTripTest {
    @Test
    fun exportedEntriesImportBackIntoAnEmptyLibraryUnchanged() =
        runTest {
            val entries =
                listOf(
                    exportEntry(1, "Cowboy Bebop", 26, WatchStatus.COMPLETED, 26, 9),
                    exportEntry(
                        52991,
                        "Sousou no Frieren: <Beyond> & \"Journey's\" End",
                        28,
                        WatchStatus.WATCHING,
                        3,
                        null,
                    ),
                    exportEntry(5, "Trigun", null, WatchStatus.ON_HOLD, 4, 6),
                    exportEntry(6, "Monster", 74, WatchStatus.DROPPED, 10, 1),
                    exportEntry(7, "Planned", 12, WatchStatus.PLAN_TO_WATCH, 0, null),
                )
            val xml = MalXmlWriter().write(entries)
            val local = FakeLibraryLocalDataSource()

            val file = (MalXmlParser().parse(xml.encodeToByteArray()) as Result.Success).data
            val summary =
                ImportMalFileUseCase(
                    MergeMalEntryUseCase(local, FakeConflictLocalDataSource(), FakeOnMalLocalDataSource()),
                    FakeSyncScheduler(),
                )(file) {}

            assertEquals(ImportSummary(added = 5, conflicts = 0, invalid = 0), summary)
            assertEquals(entries.map { it.anime.malId }, local.savedEntries.map { it.malId })
            assertEquals(entries.map { it.anime.title }, local.savedAnime.map { it.title })
            assertEquals(entries.map { it.anime.episodes }, local.savedAnime.map { it.episodes })
            assertEquals(
                entries.map { SavedEntry(it.anime.malId, it.status, it.progress, it.score) },
                local.savedEntries,
            )
        }
}
