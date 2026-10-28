package com.greenzean.animekmp.shared.details.presentation

import app.cash.turbine.test
import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.library.domain.SyncState
import com.greenzean.animekmp.shared.library.domain.WatchStatus
import com.greenzean.animekmp.shared.library.presentation.SavedEntry
import com.greenzean.animekmp.shared.mal.domain.MalApiError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class DetailsEntryEditViewModelTest : DetailsViewModelTestBase() {
    @Test
    fun noEntryWhenTheAnimeIsNotInTheLibrary() =
        runTest {
            api.result = Result.Success(frieren)
            val viewModel = viewModel()
            advanceUntilIdle()

            assertNull(viewModel.state.value.entry)
        }

    @Test
    fun addCreatesAPlanToWatchEntryWithNoProgress() =
        runTest {
            api.result = Result.Success(frieren)
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.onAction(DetailsAction.OnAddClick)
            advanceUntilIdle()

            assertEquals(EntryEditUi(WatchStatus.PLAN_TO_WATCH, 0, 28, null, 28), viewModel.state.value.entry)
        }

    @Test
    fun entryShowsStatusProgressScoreAndTheTotalFromTheDetails() =
        runTest {
            local.put(frierenAnime, SyncState.SYNCED, WatchStatus.WATCHING, progress = 5, score = 9)
            api.result = Result.Success(frieren)
            val viewModel = viewModel()
            advanceUntilIdle()

            assertEquals(EntryEditUi(WatchStatus.WATCHING, 5, 28, 9, 28), viewModel.state.value.entry)
        }

    @Test
    fun theDetailsTotalWinsOverTheStoredOne() =
        runTest {
            local.put(frierenAnime.copy(episodes = 12), SyncState.SYNCED, WatchStatus.WATCHING, progress = 5)
            api.result = Result.Success(frieren)
            val viewModel = viewModel()
            advanceUntilIdle()

            assertEquals(
                28,
                viewModel.state.value.entry
                    ?.totalEpisodes,
            )
        }

    @Test
    fun offlineStillShowsTheEditorWithTheStoredTotal() =
        runTest {
            local.put(frierenAnime.copy(episodes = 28), SyncState.SYNCED, WatchStatus.WATCHING, progress = 5, score = 8)
            api.result = Result.Error(MalApiError.NO_CONNECTION)
            val viewModel = viewModel()
            advanceUntilIdle()

            val state = viewModel.state.value
            assertEquals(DetailsStatus.Offline, state.status)
            assertNull(state.details)
            assertEquals(EntryEditUi(WatchStatus.WATCHING, 5, 28, 8, 28), state.entry)
        }

    @Test
    fun offlineWithAnUnknownTotalHasNoTotal() =
        runTest {
            local.put(frierenAnime, SyncState.SYNCED, WatchStatus.WATCHING, progress = 5)
            api.result = Result.Error(MalApiError.NO_CONNECTION)
            val viewModel = viewModel()
            advanceUntilIdle()

            assertNull(
                viewModel.state.value.entry
                    ?.totalEpisodes,
            )
        }

    @Test
    fun errorStatusesKeepTheEditor() =
        runTest {
            local.put(frierenAnime, SyncState.SYNCED, WatchStatus.WATCHING, progress = 5)
            api.result = Result.Error(MalApiError.SERVER)
            val viewModel = viewModel()
            advanceUntilIdle()

            assertEquals(DetailsStatus.Error, viewModel.state.value.status)
            assertEquals(
                5,
                viewModel.state.value.entry
                    ?.progress,
            )
        }

    @Test
    fun statusChangeSavesTheStatus() =
        runTest {
            local.put(frierenAnime, SyncState.SYNCED, WatchStatus.WATCHING, progress = 5)
            api.result = Result.Success(frieren)
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.onAction(DetailsAction.OnStatusChange(WatchStatus.ON_HOLD))
            advanceUntilIdle()

            assertEquals(SavedEntry(52991, WatchStatus.ON_HOLD, 5, null), local.savedEntries.single())
            assertEquals(
                WatchStatus.ON_HOLD,
                viewModel.state.value.entry
                    ?.status,
            )
            assertEquals(1, scheduler.requests)
        }

    @Test
    fun choosingCompletedSetsProgressToTheTotal() =
        runTest {
            local.put(frierenAnime.copy(episodes = 28), SyncState.SYNCED, WatchStatus.WATCHING, progress = 5)
            api.result = Result.Success(frieren)
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.onAction(DetailsAction.OnStatusChange(WatchStatus.COMPLETED))
            advanceUntilIdle()

            assertEquals(EntryEditUi(WatchStatus.COMPLETED, 28, 28, null, 28), viewModel.state.value.entry)
        }

    @Test
    fun selectableEpisodesIsTheTotalForAFinishedShow() =
        runTest {
            local.put(frierenAnime, SyncState.SYNCED, WatchStatus.WATCHING, progress = 5)
            api.result = Result.Success(frieren)
            val viewModel = viewModel()
            advanceUntilIdle()

            assertEquals(
                28,
                viewModel.state.value.entry
                    ?.selectableEpisodes,
            )
        }

    @Test
    fun selectableEpisodesFollowsTheClockForAnAiringShow() =
        runTest {
            local.put(frierenAnime, SyncState.SYNCED, WatchStatus.WATCHING, progress = 1)
            val airing = frieren.copy(summary = frieren.summary.copy(status = "currently_airing"))
            api.result = Result.Success(airing)
            val viewModel = viewModel()
            advanceUntilIdle()

            assertEquals(
                3,
                viewModel.state.value.entry
                    ?.selectableEpisodes,
            )
        }

    @Test
    fun selectableEpisodesUsesTheStoredTotalOffline() =
        runTest {
            local.put(frierenAnime.copy(episodes = 28), SyncState.SYNCED, WatchStatus.WATCHING, progress = 5)
            api.result = Result.Error(MalApiError.NO_CONNECTION)
            val viewModel = viewModel()
            advanceUntilIdle()

            assertEquals(
                28,
                viewModel.state.value.entry
                    ?.selectableEpisodes,
            )
        }

    @Test
    fun choosingPlanToWatchResetsProgressToZero() =
        runTest {
            local.put(frierenAnime, SyncState.SYNCED, WatchStatus.WATCHING, progress = 5, score = 8)
            api.result = Result.Success(frieren)
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.onAction(DetailsAction.OnStatusChange(WatchStatus.PLAN_TO_WATCH))
            advanceUntilIdle()

            assertEquals(SavedEntry(52991, WatchStatus.PLAN_TO_WATCH, 0, 8), local.savedEntries.single())
            assertEquals(EntryEditUi(WatchStatus.PLAN_TO_WATCH, 0, 28, 8, 28), viewModel.state.value.entry)
        }

    @Test
    fun loweringACompletedEntryGivesWatching() =
        runTest {
            local.put(frierenAnime.copy(episodes = 28), SyncState.SYNCED, WatchStatus.COMPLETED, progress = 28)
            api.result = Result.Success(frieren)
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.onAction(DetailsAction.OnProgressChange(27))
            advanceUntilIdle()

            assertEquals(EntryEditUi(WatchStatus.WATCHING, 27, 28, null, 28), viewModel.state.value.entry)
        }

    @Test
    fun progressChangeSavesTheProgress() =
        runTest {
            local.put(frierenAnime, SyncState.SYNCED, WatchStatus.WATCHING, progress = 5)
            api.result = Result.Success(frieren)
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.onAction(DetailsAction.OnProgressChange(6))
            advanceUntilIdle()

            assertEquals(
                6,
                viewModel.state.value.entry
                    ?.progress,
            )
            assertEquals(1, scheduler.requests)
        }

    @Test
    fun progressWhilePlanToWatchStartsWatching() =
        runTest {
            local.put(frierenAnime, SyncState.SYNCED, WatchStatus.PLAN_TO_WATCH)
            api.result = Result.Success(frieren)
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.onAction(DetailsAction.OnProgressChange(1))
            advanceUntilIdle()

            assertEquals(
                WatchStatus.WATCHING,
                viewModel.state.value.entry
                    ?.status,
            )
        }

    @Test
    fun progressReachingTheTotalCompletes() =
        runTest {
            local.put(frierenAnime, SyncState.SYNCED, WatchStatus.WATCHING, progress = 5)
            api.result = Result.Success(frieren)
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.onAction(DetailsAction.OnProgressChange(28))
            advanceUntilIdle()

            assertEquals(
                WatchStatus.COMPLETED,
                viewModel.state.value.entry
                    ?.status,
            )
        }

    @Test
    fun progressBeyondTheTotalIsClamped() =
        runTest {
            local.put(frierenAnime, SyncState.SYNCED, WatchStatus.WATCHING, progress = 5)
            api.result = Result.Success(frieren)
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.onAction(DetailsAction.OnProgressChange(500))
            advanceUntilIdle()

            assertEquals(
                28,
                viewModel.state.value.entry
                    ?.progress,
            )
        }

    @Test
    fun scoreChangeSavesAndClearsTheScore() =
        runTest {
            local.put(frierenAnime, SyncState.SYNCED, WatchStatus.WATCHING, progress = 5)
            api.result = Result.Success(frieren)
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.onAction(DetailsAction.OnScoreChange(9))
            advanceUntilIdle()
            assertEquals(
                9,
                viewModel.state.value.entry
                    ?.score,
            )

            viewModel.onAction(DetailsAction.OnScoreChange(null))
            advanceUntilIdle()
            assertNull(
                viewModel.state.value.entry
                    ?.score,
            )
        }

    @Test
    fun scoreOutOfRangeIsIgnored() =
        runTest {
            local.put(frierenAnime, SyncState.SYNCED, WatchStatus.WATCHING, progress = 5, score = 7)
            api.result = Result.Success(frieren)
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.onAction(DetailsAction.OnScoreChange(11))
            advanceUntilIdle()

            assertEquals(
                7,
                viewModel.state.value.entry
                    ?.score,
            )
        }

    @Test
    fun editsWorkOfflineFromTheLocalEntry() =
        runTest {
            local.put(frierenAnime.copy(episodes = 28), SyncState.SYNCED, WatchStatus.WATCHING, progress = 5)
            api.result = Result.Error(MalApiError.NO_CONNECTION)
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.onAction(DetailsAction.OnProgressChange(6))
            advanceUntilIdle()

            assertEquals(
                6,
                viewModel.state.value.entry
                    ?.progress,
            )
        }

    @Test
    fun removeDeletesTheEntryAndShowsTheRemovedMessage() =
        runTest {
            local.put(frierenAnime, SyncState.SYNCED, WatchStatus.WATCHING, progress = 5)
            api.result = Result.Success(frieren)
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.events.test {
                viewModel.onAction(DetailsAction.OnRemoveClick)
                advanceUntilIdle()

                assertEquals(DetailsEvent.ShowRemoved, awaitItem())
            }
            assertEquals(listOf(52991), local.removedMalIds)
            assertNull(viewModel.state.value.entry)
            assertEquals(false, viewModel.state.value.inLibrary)
            assertEquals(1, scheduler.requests)
        }

    @Test
    fun removeWhenNotInTheLibraryDoesNothing() =
        runTest {
            api.result = Result.Success(frieren)
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.events.test {
                viewModel.onAction(DetailsAction.OnRemoveClick)
                advanceUntilIdle()

                expectNoEvents()
            }
            assertEquals(emptyList(), local.removedMalIds)
        }

    @Test
    fun undoRestoresTheRemovedEntryWithItsValues() =
        runTest {
            local.put(frierenAnime, SyncState.SYNCED, WatchStatus.WATCHING, progress = 5, score = 9)
            api.result = Result.Success(frieren)
            val viewModel = viewModel()
            advanceUntilIdle()
            viewModel.onAction(DetailsAction.OnRemoveClick)
            advanceUntilIdle()

            viewModel.onAction(DetailsAction.OnUndoRemoveClick)
            advanceUntilIdle()

            assertEquals(EntryEditUi(WatchStatus.WATCHING, 5, 28, 9, 28), viewModel.state.value.entry)
            assertEquals(SavedEntry(52991, WatchStatus.WATCHING, 5, 9), local.savedEntries.single())
            assertEquals(2, scheduler.requests)
        }

    @Test
    fun undoWithoutARemoveDoesNothing() =
        runTest {
            local.put(frierenAnime, SyncState.SYNCED, WatchStatus.WATCHING, progress = 5)
            api.result = Result.Success(frieren)
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.onAction(DetailsAction.OnUndoRemoveClick)
            advanceUntilIdle()

            assertEquals(emptyList(), local.savedEntries)
            assertEquals(0, scheduler.requests)
        }

    @Test
    fun undoTwiceRestoresOnce() =
        runTest {
            local.put(frierenAnime, SyncState.SYNCED, WatchStatus.WATCHING, progress = 5)
            api.result = Result.Success(frieren)
            val viewModel = viewModel()
            advanceUntilIdle()
            viewModel.onAction(DetailsAction.OnRemoveClick)
            advanceUntilIdle()

            viewModel.onAction(DetailsAction.OnUndoRemoveClick)
            viewModel.onAction(DetailsAction.OnUndoRemoveClick)
            advanceUntilIdle()

            assertEquals(1, local.savedEntries.size)
        }
}
