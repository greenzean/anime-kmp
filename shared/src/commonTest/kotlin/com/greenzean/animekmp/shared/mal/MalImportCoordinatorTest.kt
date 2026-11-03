package com.greenzean.animekmp.shared.mal

import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.library.domain.WatchStatus
import com.greenzean.animekmp.shared.mal.domain.ImportMalListUseCase
import com.greenzean.animekmp.shared.mal.domain.MalApiError
import com.greenzean.animekmp.shared.mal.domain.MalImportCoordinator
import com.greenzean.animekmp.shared.mal.domain.MalImportStatus
import com.greenzean.animekmp.shared.mal.domain.MalListApi
import com.greenzean.animekmp.shared.mal.domain.MalListPage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class MalImportCoordinatorTest {
    private fun TestScope.scope(): CoroutineScope = backgroundScope + UnconfinedTestDispatcher(testScheduler)

    private fun TestScope.harness() = MalImportHarness(scope())

    @Test
    fun startsIdle() =
        runTest {
            val h = harness()

            assertEquals(MalImportStatus.Idle, h.coordinator.status.value)
        }

    @Test
    fun successfulImportEndsDoneWithTheCounts() =
        runTest {
            val h = harness()
            h.api.results[0] = listPage(0, 3, hasNext = false)

            h.coordinator.start()
            advanceUntilIdle()

            assertEquals(MalImportStatus.Done(added = 3, conflicts = 0), h.coordinator.status.value)
        }

    @Test
    fun statusIsImportingWhileThePageIsPending() =
        runTest {
            val h = harness()
            h.api.gates[0] = CompletableDeferred()

            h.coordinator.start()

            assertIs<MalImportStatus.Importing>(h.coordinator.status.value)
        }

    @Test
    fun importingCarriesTheCountOfEntriesMergedSoFar() =
        runTest {
            val h = harness()
            h.api.results[0] = listPage(0, 100, hasNext = true)
            h.api.gates[100] = CompletableDeferred()

            h.coordinator.start()
            advanceUntilIdle()

            assertEquals(MalImportStatus.Importing(100), h.coordinator.status.value)
        }

    @Test
    fun releasingThePendingPageFinishesTheImport() =
        runTest {
            val h = harness()
            h.api.results[0] = listPage(0, 2, hasNext = false)
            h.api.gates[0] = CompletableDeferred()

            h.coordinator.start()
            h.api.gates
                .getValue(0)
                .complete(Unit)
            advanceUntilIdle()

            assertEquals(MalImportStatus.Done(2, 0), h.coordinator.status.value)
        }

    @Test
    fun startingWhileImportingDoesNotStartASecondImport() =
        runTest {
            val h = harness()
            h.api.results[0] = listPage(0, 2, hasNext = false)
            h.api.gates[0] = CompletableDeferred()

            h.coordinator.start()
            h.coordinator.start()
            h.start()
            h.api.gates
                .getValue(0)
                .complete(Unit)
            advanceUntilIdle()

            assertEquals(listOf(0), h.api.offsets)
            assertEquals(MalImportStatus.Done(2, 0), h.coordinator.status.value)
        }

    @Test
    fun failedImportEndsInError() =
        runTest {
            val h = harness()
            h.api.results[0] = Result.Error(MalApiError.NO_CONNECTION)

            h.coordinator.start()
            advanceUntilIdle()

            assertEquals(MalImportStatus.Error, h.coordinator.status.value)
        }

    @Test
    fun startAfterAnErrorRunsAgainAndResumesFromTheKeptOffset() =
        runTest {
            val h = harness()
            h.api.results[0] = listPage(0, 100, hasNext = true)
            h.api.results[100] = Result.Error(MalApiError.NO_CONNECTION)
            h.coordinator.start()
            advanceUntilIdle()
            assertEquals(MalImportStatus.Error, h.coordinator.status.value)

            h.api.results[100] = listPage(100, 2, hasNext = false)
            h.coordinator.start()
            advanceUntilIdle()

            assertEquals(listOf(0, 100, 100), h.api.offsets)
            assertEquals(MalImportStatus.Done(2, 0), h.coordinator.status.value)
        }

    @Test
    fun startAfterDoneRunsAnotherImport() =
        runTest {
            val h = harness()
            h.coordinator.start()
            advanceUntilIdle()
            assertEquals(MalImportStatus.Done(0, 0), h.coordinator.status.value)

            h.coordinator.start()
            advanceUntilIdle()

            assertEquals(listOf(0, 0), h.api.offsets)
        }

    @Test
    fun observeEmitsTheCoordinatorStatus() =
        runTest {
            val h = harness()
            h.api.results[0] = listPage(0, 1, hasNext = false)

            h.start()
            advanceUntilIdle()

            assertEquals(MalImportStatus.Done(1, 0), h.observe().first())
        }

    @Test
    fun anUnexpectedFailureEndsInErrorInsteadOfCrashing() =
        runTest {
            val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
            val h = MalImportHarness(scope)
            val failing =
                object : MalListApi {
                    override suspend fun page(
                        offset: Int,
                        limit: Int,
                    ): Result<MalListPage, MalApiError> = error("boom")

                    override suspend fun update(
                        malId: Int,
                        status: WatchStatus,
                        progress: Int,
                        score: Int?,
                    ): Result<Unit, MalApiError> = error("boom")

                    override suspend fun delete(malId: Int): Result<Unit, MalApiError> = error("boom")
                }
            val coordinator =
                MalImportCoordinator(
                    ImportMalListUseCase(
                        failing,
                        h.throttle,
                        h.merge,
                        h.store,
                        h.scheduler,
                        h.outboxHarness.queueAppOnly,
                        pullStore = h.pullStore,
                        baseStore = h.baseStore,
                    ),
                    scope,
                )

            coordinator.start()
            advanceUntilIdle()

            assertEquals(MalImportStatus.Error, coordinator.status.value)
            scope.cancel()
        }
}
