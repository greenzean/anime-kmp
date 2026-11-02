package com.greenzean.animekmp.shared.export

import app.cash.turbine.test
import com.greenzean.animekmp.shared.core.domain.EmptyResult
import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.export.domain.ExportError
import com.greenzean.animekmp.shared.export.domain.ExportFileSaver
import com.greenzean.animekmp.shared.export.domain.ExportLibraryToMalUseCase
import com.greenzean.animekmp.shared.export.domain.MalXmlWriter
import com.greenzean.animekmp.shared.export.domain.SaveExportFileUseCase
import com.greenzean.animekmp.shared.export.presentation.ExportAction
import com.greenzean.animekmp.shared.export.presentation.ExportEvent
import com.greenzean.animekmp.shared.export.presentation.ExportStatus
import com.greenzean.animekmp.shared.export.presentation.ExportViewModel
import com.greenzean.animekmp.shared.library.domain.Anime
import com.greenzean.animekmp.shared.library.domain.ObserveLibraryUseCase
import com.greenzean.animekmp.shared.library.domain.SyncState
import com.greenzean.animekmp.shared.library.presentation.FakeLibraryLocalDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class FakeExportFileSaver : ExportFileSaver {
    val saved = mutableListOf<Pair<String, String>>()
    var failure: ExportError? = null

    override suspend fun save(
        destination: String,
        content: String,
    ): EmptyResult<ExportError> {
        saved += destination to content
        return failure?.let { Result.Error(it) } ?: Result.Success(Unit)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ExportViewModelTest {
    private val local = FakeLibraryLocalDataSource()
    private val saver = FakeExportFileSaver()
    private val clock =
        object : Clock {
            override fun now(): Instant = Instant.parse("2026-03-05T10:00:00Z")
        }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() =
        ExportViewModel(
            ExportLibraryToMalUseCase(ObserveLibraryUseCase(local), MalXmlWriter(), clock),
            SaveExportFileUseCase(saver),
        )

    private fun addEntry() = local.put(Anime(1, "Cowboy Bebop"), SyncState.SYNCED)

    @Test
    fun startsIdle() {
        assertEquals(ExportStatus.Idle, viewModel().state.value.status)
    }

    @Test
    fun exportClickLaunchesSaveDialogAndStaysExporting() =
        runTest {
            addEntry()
            val viewModel = viewModel()

            viewModel.events.test {
                viewModel.onAction(ExportAction.OnExportClick)
                advanceUntilIdle()

                assertEquals(ExportEvent.LaunchSaveDialog("animekmp-mal-export-2026-03-05.xml"), awaitItem())
                assertEquals(ExportStatus.Exporting, viewModel.state.value.status)
            }
        }

    @Test
    fun exportClickOnEmptyLibraryShowsEmptyLibraryError() =
        runTest {
            val viewModel = viewModel()

            viewModel.onAction(ExportAction.OnExportClick)
            advanceUntilIdle()

            assertEquals(ExportStatus.Error(ExportError.EmptyLibrary), viewModel.state.value.status)
        }

    @Test
    fun cancelledDialogReturnsToIdle() =
        runTest {
            addEntry()
            val viewModel = viewModel()
            viewModel.onAction(ExportAction.OnExportClick)
            advanceUntilIdle()

            viewModel.onAction(ExportAction.OnFileChosen(null))
            advanceUntilIdle()

            assertEquals(ExportStatus.Idle, viewModel.state.value.status)
            assertTrue(saver.saved.isEmpty())
        }

    @Test
    fun chosenFileIsSavedWithTheDocumentContentAndEndsDone() =
        runTest {
            addEntry()
            val viewModel = viewModel()
            viewModel.onAction(ExportAction.OnExportClick)
            advanceUntilIdle()

            viewModel.onAction(ExportAction.OnFileChosen("content://dest"))
            advanceUntilIdle()

            assertEquals(ExportStatus.Done(1), viewModel.state.value.status)
            assertEquals("content://dest", saver.saved.single().first)
            assertTrue(
                saver.saved
                    .single()
                    .second
                    .contains("<series_animedb_id>1</series_animedb_id>"),
            )
        }

    @Test
    fun saveFailureEndsWithWriteFailed() =
        runTest {
            addEntry()
            saver.failure = ExportError.WriteFailed
            val viewModel = viewModel()
            viewModel.onAction(ExportAction.OnExportClick)
            advanceUntilIdle()

            viewModel.onAction(ExportAction.OnFileChosen("content://dest"))
            advanceUntilIdle()

            assertEquals(ExportStatus.Error(ExportError.WriteFailed), viewModel.state.value.status)
        }

    @Test
    fun fileChosenWithoutPendingDocumentIsIgnored() =
        runTest {
            val viewModel = viewModel()

            viewModel.onAction(ExportAction.OnFileChosen("content://dest"))
            advanceUntilIdle()

            assertEquals(ExportStatus.Idle, viewModel.state.value.status)
            assertTrue(saver.saved.isEmpty())
        }

    @Test
    fun retryAfterErrorExportsAgain() =
        runTest {
            val viewModel = viewModel()
            viewModel.onAction(ExportAction.OnExportClick)
            advanceUntilIdle()
            addEntry()

            viewModel.events.test {
                viewModel.onAction(ExportAction.OnExportClick)
                advanceUntilIdle()

                assertEquals(ExportEvent.LaunchSaveDialog("animekmp-mal-export-2026-03-05.xml"), awaitItem())
            }
        }
}
