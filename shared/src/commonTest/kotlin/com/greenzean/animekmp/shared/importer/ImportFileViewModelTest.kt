package com.greenzean.animekmp.shared.importer

import app.cash.turbine.test
import com.greenzean.animekmp.shared.auth.FakeSyncScheduler
import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.importer.domain.FillImportedImagesUseCase
import com.greenzean.animekmp.shared.importer.domain.ImportError
import com.greenzean.animekmp.shared.importer.domain.ImportFileReader
import com.greenzean.animekmp.shared.importer.domain.ImportMalFileUseCase
import com.greenzean.animekmp.shared.importer.domain.MalXmlParser
import com.greenzean.animekmp.shared.importer.domain.ReadImportFileUseCase
import com.greenzean.animekmp.shared.importer.presentation.ImportFileAction
import com.greenzean.animekmp.shared.importer.presentation.ImportFileEvent
import com.greenzean.animekmp.shared.importer.presentation.ImportFileViewModel
import com.greenzean.animekmp.shared.importer.presentation.ImportStatus
import com.greenzean.animekmp.shared.library.domain.Anime
import com.greenzean.animekmp.shared.library.domain.SyncState
import com.greenzean.animekmp.shared.library.domain.WatchStatus
import com.greenzean.animekmp.shared.library.presentation.FakeLibraryLocalDataSource
import com.greenzean.animekmp.shared.sync.domain.ParkedAnimeResolver
import kotlinx.coroutines.CompletableDeferred
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

class FakeImportFileReader : ImportFileReader {
    val sources = mutableListOf<String>()
    var bytes: ByteArray = ByteArray(0)
    var failure: ImportError? = null
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun read(source: String): Result<ByteArray, ImportError> {
        sources += source
        gate?.await()
        return failure?.let { Result.Error(it) } ?: Result.Success(bytes)
    }
}

class CountingParkedAnimeResolver : ParkedAnimeResolver {
    var calls = 0

    override suspend fun resolve() {
        calls++
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ImportFileViewModelTest {
    private val local = FakeLibraryLocalDataSource()
    private val reader = FakeImportFileReader()
    private val scheduler = FakeSyncScheduler()
    private val resolver = CountingParkedAnimeResolver()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() =
        ImportFileViewModel(
            ReadImportFileUseCase(reader, MalXmlParser()),
            ImportMalFileUseCase(local, scheduler),
            FillImportedImagesUseCase(resolver),
        )

    private fun serve(xml: String) {
        reader.bytes = xml.encodeToByteArray()
    }

    @Test
    fun importedAnimeImagesAreFilledAfterTheImportEvenWhenNothingWasAdded() =
        runTest {
            local.put(Anime(1, "Cowboy Bebop"), SyncState.SYNCED, WatchStatus.WATCHING, 0)
            serve(malXml(malAnimeXml(id = "1")))
            val viewModel = viewModel()

            viewModel.onAction(ImportFileAction.OnFileChosen("content://file"))
            advanceUntilIdle()

            assertEquals(1, resolver.calls)
        }

    @Test
    fun imagesAreNotFilledWhenTheFileCannotBeRead() =
        runTest {
            reader.failure = ImportError.Unreadable
            val viewModel = viewModel()

            viewModel.onAction(ImportFileAction.OnFileChosen("content://file"))
            advanceUntilIdle()

            assertEquals(0, resolver.calls)
        }

    @Test
    fun startsIdle() {
        assertEquals(ImportStatus.Idle, viewModel().state.value.status)
    }

    @Test
    fun importClickLaunchesOpenDialog() =
        runTest {
            val viewModel = viewModel()

            viewModel.events.test {
                viewModel.onAction(ImportFileAction.OnImportClick)
                advanceUntilIdle()

                assertEquals(ImportFileEvent.LaunchOpenDialog, awaitItem())
            }
        }

    @Test
    fun cancelledDialogStaysIdleAndReadsNothing() =
        runTest {
            val viewModel = viewModel()

            viewModel.onAction(ImportFileAction.OnFileChosen(null))
            advanceUntilIdle()

            assertEquals(ImportStatus.Idle, viewModel.state.value.status)
            assertEquals(emptyList(), reader.sources)
        }

    @Test
    fun chosenFileEndsDoneWithSummary() =
        runTest {
            local.put(Anime(2, "Trigun"), SyncState.SYNCED, WatchStatus.DROPPED, 1)
            serve(
                malXml(
                    malAnimeXml(id = "1"),
                    malAnimeXml(id = "2"),
                    malAnimeXml(id = "3", status = "Bingeing"),
                ),
            )
            val viewModel = viewModel()

            viewModel.onAction(ImportFileAction.OnFileChosen("content://file"))
            advanceUntilIdle()

            assertEquals(listOf("content://file"), reader.sources)
            assertEquals(ImportStatus.Done(added = 1, keptYours = 1, invalid = 1), viewModel.state.value.status)
            assertEquals(1, scheduler.requests)
        }

    @Test
    fun statusIsImportingWhileReadingThenDone() =
        runTest {
            serve(malXml(malAnimeXml(id = "1")))
            reader.gate = CompletableDeferred()
            val viewModel = viewModel()

            viewModel.state.test {
                assertEquals(ImportStatus.Idle, awaitItem().status)
                viewModel.onAction(ImportFileAction.OnFileChosen("content://file"))
                assertEquals(ImportStatus.Importing(0f), awaitItem().status)
                reader.gate!!.complete(Unit)
                var last = awaitItem().status
                while (last is ImportStatus.Importing) last = awaitItem().status
                assertEquals(ImportStatus.Done(1, 0, 0), last)
            }
        }

    @Test
    fun readFailureEndsWithUnreadable() =
        runTest {
            reader.failure = ImportError.Unreadable
            val viewModel = viewModel()

            viewModel.onAction(ImportFileAction.OnFileChosen("content://file"))
            advanceUntilIdle()

            assertEquals(ImportStatus.Error(ImportError.Unreadable), viewModel.state.value.status)
        }

    @Test
    fun notMalFileEndsWithNotMalExport() =
        runTest {
            serve("<feed></feed>")
            val viewModel = viewModel()

            viewModel.onAction(ImportFileAction.OnFileChosen("content://file"))
            advanceUntilIdle()

            assertEquals(ImportStatus.Error(ImportError.NotMalExport), viewModel.state.value.status)
        }

    @Test
    fun importClickIsIgnoredWhileImporting() =
        runTest {
            serve(malXml(malAnimeXml(id = "1")))
            reader.gate = CompletableDeferred()
            val viewModel = viewModel()
            viewModel.onAction(ImportFileAction.OnFileChosen("content://file"))

            viewModel.events.test {
                viewModel.onAction(ImportFileAction.OnImportClick)
                advanceUntilIdle()

                expectNoEvents()
            }
            reader.gate!!.complete(Unit)
            advanceUntilIdle()
            assertEquals(ImportStatus.Done(1, 0, 0), viewModel.state.value.status)
        }

    @Test
    fun retryAfterErrorLaunchesDialogAgain() =
        runTest {
            reader.failure = ImportError.Unreadable
            val viewModel = viewModel()
            viewModel.onAction(ImportFileAction.OnFileChosen("content://file"))
            advanceUntilIdle()

            viewModel.events.test {
                viewModel.onAction(ImportFileAction.OnImportClick)
                advanceUntilIdle()

                assertEquals(ImportFileEvent.LaunchOpenDialog, awaitItem())
            }
        }
}
