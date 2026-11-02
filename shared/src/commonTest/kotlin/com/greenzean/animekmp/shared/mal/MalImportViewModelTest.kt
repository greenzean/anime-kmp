package com.greenzean.animekmp.shared.mal

import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.mal.domain.MalApiError
import com.greenzean.animekmp.shared.mal.domain.MalImportStatus
import com.greenzean.animekmp.shared.mal.presentation.MalImportAction
import com.greenzean.animekmp.shared.mal.presentation.MalImportState
import com.greenzean.animekmp.shared.mal.presentation.MalImportViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class MalImportViewModelTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class Setup(
        val harness: MalImportHarness,
        val viewModel: MalImportViewModel,
    )

    private fun TestScope.setup(): Setup {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val harness =
            MalImportHarness(backgroundScope + dispatcher)
        val viewModel = MalImportViewModel(harness.observe, harness.start)
        backgroundScope.launch(dispatcher) { viewModel.state.collect { } }
        return Setup(harness, viewModel)
    }

    @Test
    fun initialStateIsIdle() =
        runTest {
            val s = setup()

            assertEquals(MalImportState(MalImportStatus.Idle), s.viewModel.state.value)
        }

    @Test
    fun onImportClickStartsTheImport() =
        runTest {
            val s = setup()
            s.harness.api.results[0] = listPage(0, 2, hasNext = false)

            s.viewModel.onAction(MalImportAction.OnImportClick)
            advanceUntilIdle()

            assertEquals(listOf(0), s.harness.api.offsets)
            assertEquals(MalImportState(MalImportStatus.Done(2, 0)), s.viewModel.state.value)
        }

    @Test
    fun stateShowsImportingWhileTheImportRuns() =
        runTest {
            val s = setup()
            s.harness.api.gates[0] = CompletableDeferred()

            s.viewModel.onAction(MalImportAction.OnImportClick)

            assertIs<MalImportStatus.Importing>(s.viewModel.state.value.status)
        }

    @Test
    fun onRetryClickStartsTheImportAgainAfterAnError() =
        runTest {
            val s = setup()
            s.harness.api.results[0] = Result.Error(MalApiError.NO_CONNECTION)
            s.viewModel.onAction(MalImportAction.OnImportClick)
            advanceUntilIdle()
            assertEquals(MalImportState(MalImportStatus.Error), s.viewModel.state.value)

            s.harness.api.results[0] = listPage(0, 1, hasNext = false)
            s.viewModel.onAction(MalImportAction.OnRetryClick)
            advanceUntilIdle()

            assertEquals(listOf(0, 0), s.harness.api.offsets)
            assertEquals(MalImportState(MalImportStatus.Done(1, 0)), s.viewModel.state.value)
        }

    @Test
    fun stateFollowsAnImportStartedElsewhere() =
        runTest {
            val s = setup()
            s.harness.api.results[0] = listPage(0, 2, hasNext = false)

            s.harness.start()
            advanceUntilIdle()

            assertEquals(MalImportState(MalImportStatus.Done(2, 0)), s.viewModel.state.value)
        }

    @Test
    fun clickingWhileImportingDoesNotStartASecondImport() =
        runTest {
            val s = setup()
            s.harness.api.gates[0] = CompletableDeferred()

            s.viewModel.onAction(MalImportAction.OnImportClick)
            s.viewModel.onAction(MalImportAction.OnImportClick)

            assertEquals(listOf(0), s.harness.api.offsets)
        }
}
