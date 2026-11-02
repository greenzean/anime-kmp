package com.greenzean.animekmp.shared.conflict

import app.cash.turbine.test
import com.greenzean.animekmp.shared.auth.FakeSyncScheduler
import com.greenzean.animekmp.shared.conflict.domain.ObserveConflictsUseCase
import com.greenzean.animekmp.shared.conflict.domain.ResolveAllConflictsUseCase
import com.greenzean.animekmp.shared.conflict.domain.ResolveConflictUseCase
import com.greenzean.animekmp.shared.conflict.presentation.ConflictUi
import com.greenzean.animekmp.shared.conflict.presentation.ConflictsAction
import com.greenzean.animekmp.shared.conflict.presentation.ConflictsViewModel
import com.greenzean.animekmp.shared.library.domain.Anime
import com.greenzean.animekmp.shared.library.domain.SyncState
import com.greenzean.animekmp.shared.library.domain.WatchStatus
import com.greenzean.animekmp.shared.library.presentation.FakeLibraryLocalDataSource
import com.greenzean.animekmp.shared.library.presentation.SavedEntry
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ConflictsViewModelTest {
    private val library = FakeLibraryLocalDataSource()
    private val conflicts = FakeConflictLocalDataSource()
    private val scheduler = FakeSyncScheduler()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() =
        ConflictsViewModel(
            ObserveConflictsUseCase(conflicts),
            ResolveConflictUseCase(conflicts, library, scheduler),
            ResolveAllConflictsUseCase(conflicts, library, scheduler),
        )

    private fun seed(vararg ids: Int) {
        ids.forEach { id ->
            library.put(Anime(id, "Anime $id", episodes = 12), SyncState.SYNCED, WatchStatus.WATCHING, 5, 7)
            conflicts.put(conflict(malId = id, title = "Anime $id", other = values(WatchStatus.COMPLETED, 12, 9)))
        }
    }

    @Test
    fun startsLoadingWithNoItems() =
        runTest {
            conflicts.gate = CompletableDeferred()

            viewModel().state.test {
                val first = awaitItem()
                assertTrue(first.isLoading)
                assertTrue(first.items.isEmpty())
                assertFalse(first.showKeepAllMalDialog)
            }
        }

    @Test
    fun loadedConflictsBecomeItemsWithBothSides() =
        runTest {
            seed(1)

            val state = viewModel().state.value

            assertFalse(state.isLoading)
            assertEquals(
                listOf(
                    ConflictUi(
                        malId = 1,
                        title = "Anime 1",
                        app = values(WatchStatus.WATCHING, 5, 7),
                        mal = values(WatchStatus.COMPLETED, 12, 9),
                    ),
                ),
                state.items,
            )
        }

    @Test
    fun theAnimeImageReachesTheItem() =
        runTest {
            library.put(Anime(1, "Anime 1", episodes = 12), SyncState.SYNCED, WatchStatus.WATCHING, 5, 7)
            conflicts.put(conflict(malId = 1, title = "Anime 1", imageUrl = "https://img.example/1.jpg"))

            val item =
                viewModel()
                    .state.value.items
                    .single()

            assertEquals("https://img.example/1.jpg", item.imageUrl)
        }

    @Test
    fun noConflictsStopsLoadingWithAnEmptyList() =
        runTest {
            val state = viewModel().state.value

            assertFalse(state.isLoading)
            assertTrue(state.items.isEmpty())
        }

    @Test
    fun loadingEndsWhenTheFirstEmissionArrives() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            conflicts.gate = gate
            seed(1)
            val viewModel = viewModel()

            viewModel.state.test {
                assertTrue(awaitItem().isLoading)
                gate.complete(Unit)
                val loaded = awaitItem()
                assertFalse(loaded.isLoading)
                assertEquals(1, loaded.items.size)
            }
        }

    @Test
    fun newConflictsAppearInTheItems() =
        runTest {
            val viewModel = viewModel()

            seed(1, 2)

            assertEquals(
                listOf(1, 2),
                viewModel.state.value.items
                    .map { it.malId },
            )
        }

    @Test
    fun keepAppRemovesTheItemAndChangesNoEntry() =
        runTest {
            seed(1, 2)
            val viewModel = viewModel()

            viewModel.onAction(ConflictsAction.OnKeepAppClick(1))
            advanceUntilIdle()

            assertEquals(
                listOf(2),
                viewModel.state.value.items
                    .map { it.malId },
            )
            assertTrue(library.savedEntries.isEmpty())
            assertEquals(0, scheduler.requests)
        }

    @Test
    fun keepMalSavesTheMalValuesRemovesTheItemAndSyncs() =
        runTest {
            seed(1, 2)
            val viewModel = viewModel()

            viewModel.onAction(ConflictsAction.OnKeepMalClick(2))
            advanceUntilIdle()

            assertEquals(
                listOf(1),
                viewModel.state.value.items
                    .map { it.malId },
            )
            assertEquals(listOf(SavedEntry(2, WatchStatus.COMPLETED, 12, 9)), library.savedEntries)
            assertEquals(1, scheduler.requests)
        }

    @Test
    fun keepAllAppClearsTheListWithoutADialog() =
        runTest {
            seed(1, 2)
            val viewModel = viewModel()

            viewModel.onAction(ConflictsAction.OnKeepAllAppClick)
            advanceUntilIdle()

            assertTrue(
                viewModel.state.value.items
                    .isEmpty(),
            )
            assertFalse(viewModel.state.value.showKeepAllMalDialog)
            assertTrue(library.savedEntries.isEmpty())
        }

    @Test
    fun keepAllMalClickOnlyOpensTheDialog() =
        runTest {
            seed(1, 2)
            val viewModel = viewModel()

            viewModel.onAction(ConflictsAction.OnKeepAllMalClick)
            advanceUntilIdle()

            assertTrue(viewModel.state.value.showKeepAllMalDialog)
            assertEquals(2, viewModel.state.value.items.size)
            assertTrue(library.savedEntries.isEmpty())
            assertEquals(0, scheduler.requests)
        }

    @Test
    fun dismissingTheDialogClosesItAndKeepsEverything() =
        runTest {
            seed(1, 2)
            val viewModel = viewModel()
            viewModel.onAction(ConflictsAction.OnKeepAllMalClick)

            viewModel.onAction(ConflictsAction.OnDismissDialog)
            advanceUntilIdle()

            assertFalse(viewModel.state.value.showKeepAllMalDialog)
            assertEquals(2, viewModel.state.value.items.size)
            assertTrue(library.savedEntries.isEmpty())
            assertEquals(0, scheduler.requests)
        }

    @Test
    fun confirmingKeepAllMalClosesTheDialogAppliesEveryMalSideAndSyncsOnce() =
        runTest {
            seed(1, 2)
            val viewModel = viewModel()
            viewModel.onAction(ConflictsAction.OnKeepAllMalClick)

            viewModel.onAction(ConflictsAction.OnKeepAllMalConfirm)
            advanceUntilIdle()

            assertFalse(viewModel.state.value.showKeepAllMalDialog)
            assertTrue(
                viewModel.state.value.items
                    .isEmpty(),
            )
            assertEquals(listOf(1, 2), library.savedEntries.map { it.malId })
            assertEquals(1, scheduler.requests)
        }
}
