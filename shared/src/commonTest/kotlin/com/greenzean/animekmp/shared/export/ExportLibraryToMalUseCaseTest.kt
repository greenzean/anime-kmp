package com.greenzean.animekmp.shared.export

import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.export.domain.ExportError
import com.greenzean.animekmp.shared.export.domain.ExportLibraryToMalUseCase
import com.greenzean.animekmp.shared.export.domain.MalXmlWriter
import com.greenzean.animekmp.shared.library.domain.Anime
import com.greenzean.animekmp.shared.library.domain.ObserveLibraryUseCase
import com.greenzean.animekmp.shared.library.domain.SyncState
import com.greenzean.animekmp.shared.library.domain.WatchStatus
import com.greenzean.animekmp.shared.library.presentation.FakeLibraryLocalDataSource
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class ExportLibraryToMalUseCaseTest {
    private val local = FakeLibraryLocalDataSource()
    private val clock =
        object : Clock {
            override fun now(): Instant = Instant.parse("2026-03-05T10:00:00Z")
        }
    private val useCase = ExportLibraryToMalUseCase(ObserveLibraryUseCase(local), MalXmlWriter(), clock)

    @Test
    fun emptyLibraryReturnsEmptyLibraryError() =
        runTest {
            val result = useCase()

            assertEquals(Result.Error(ExportError.EmptyLibrary), result)
        }

    @Test
    fun returnsDocumentWithFileNameContentAndCount() =
        runTest {
            local.put(Anime(1, "Cowboy Bebop", episodes = 26), SyncState.SYNCED, WatchStatus.COMPLETED, 26, 9)
            local.put(Anime(52991, "Sousou no Frieren", episodes = 28), SyncState.PENDING, WatchStatus.WATCHING, 3)

            val document = (useCase() as Result.Success).data
            assertEquals("animekmp-mal-export-2026-03-05.xml", document.fileName)
            assertEquals(2, document.count)
            assertTrue(document.content.contains("<series_animedb_id>1</series_animedb_id>"))
            assertTrue(document.content.contains("<series_animedb_id>52991</series_animedb_id>"))
        }

    @Test
    fun removedEntriesAreNotExported() =
        runTest {
            local.put(Anime(1, "Cowboy Bebop"), SyncState.SYNCED)
            local.put(Anime(2, "Trigun"), SyncState.SYNCED)
            local.remove(2)

            val document = (useCase() as Result.Success).data

            assertEquals(1, document.count)
            assertFalse(document.content.contains("Trigun"))
        }
}
