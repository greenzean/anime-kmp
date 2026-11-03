package com.greenzean.animekmp.shared.mal

import app.cash.turbine.test
import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.mal.domain.MalApiError
import com.greenzean.animekmp.shared.mal.domain.MalPullInfo
import com.greenzean.animekmp.shared.mal.domain.MalPullResult
import com.greenzean.animekmp.shared.mal.domain.ObserveMalPullUseCase
import com.greenzean.animekmp.shared.mal.domain.SyncMalNowUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

@OptIn(ExperimentalCoroutinesApi::class)
class MalPullCoordinatorTest {
    private fun TestScope.harness(
        loggedIn: Boolean = true,
        initialOffset: Int = 0,
    ) = MalPullHarness(backgroundScope + UnconfinedTestDispatcher(testScheduler), loggedIn, initialOffset)

    private fun MalPullHarness.gatePull(): CompletableDeferred<Unit> =
        CompletableDeferred<Unit>().also { api.gates[0] = it }

    @Test
    fun startsNotSyncing() =
        runTest {
            assertFalse(harness().coordinator.syncing.value)
        }

    @Test
    fun syncingIsTrueWhileAStartedPullIsRunning() =
        runTest {
            val h = harness()
            h.gatePull()

            h.coordinator.start()

            assertTrue(h.coordinator.syncing.value)
        }

    @Test
    fun syncingIsFalseAgainWhenTheStartedPullFinishes() =
        runTest {
            val h = harness()
            val gate = h.gatePull()
            h.coordinator.start()

            gate.complete(Unit)
            advanceUntilIdle()

            assertFalse(h.coordinator.syncing.value)
            assertEquals(listOf(PULL_NOW), h.pullStore.saved)
        }

    @Test
    fun syncingIsTrueWhileADirectPullIsRunning() =
        runTest {
            val h = harness()
            val gate = h.gatePull()

            val result = async(UnconfinedTestDispatcher(testScheduler)) { h.coordinator.pull() }

            assertTrue(h.coordinator.syncing.value)
            gate.complete(Unit)
            assertEquals(MalPullResult.DONE, result.await())
            assertFalse(h.coordinator.syncing.value)
        }

    @Test
    fun pullReturnsTheUseCaseResult() =
        runTest {
            val h = harness()
            h.api.results[0] = Result.Error(MalApiError.NO_CONNECTION)

            assertEquals(MalPullResult.RETRY, h.coordinator.pull())
            assertFalse(h.coordinator.syncing.value)
        }

    @Test
    fun syncingIsFalseAfterAFailedPull() =
        runTest {
            val h = harness()
            h.api.results[0] = Result.Error(MalApiError.SERVER)

            h.coordinator.start()
            advanceUntilIdle()

            assertFalse(h.coordinator.syncing.value)
        }

    @Test
    fun startingWhileAPullRunsDoesNotStartASecondPull() =
        runTest {
            val h = harness()
            val gate = h.gatePull()

            h.coordinator.start()
            h.coordinator.start()
            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(listOf(0), h.api.offsets)
        }

    @Test
    fun aDirectPullDoesNotOverlapARunningPull() =
        runTest {
            val h = harness()
            val gate = h.gatePull()
            h.coordinator.start()

            val second = async(UnconfinedTestDispatcher(testScheduler)) { h.coordinator.pull() }

            assertEquals(listOf(0), h.api.offsets)
            gate.complete(Unit)
            advanceUntilIdle()
            second.await()
            assertFalse(h.coordinator.syncing.value)
        }

    @Test
    fun aNewPullCanStartAfterTheLastOneFinished() =
        runTest {
            val h = harness()
            h.coordinator.start()
            advanceUntilIdle()
            h.clock.current += 1.hours

            h.coordinator.start()
            advanceUntilIdle()

            assertEquals(listOf(0, 0), h.api.offsets)
        }

    @Test
    fun aPullIsSkippedWhileTheImportIsRunning() =
        runTest {
            val h = harness()
            h.api.gates[0] = CompletableDeferred()
            h.import.coordinator.start()

            val result = h.coordinator.pull()

            assertEquals(MalPullResult.SKIPPED, result)
            assertEquals(listOf(0), h.api.offsets)
            assertFalse(h.coordinator.syncing.value)
            assertTrue(h.pullStore.saved.isEmpty())
        }

    @Test
    fun startIsSkippedWhileTheImportIsRunning() =
        runTest {
            val h = harness()
            h.api.gates[0] = CompletableDeferred()
            h.import.coordinator.start()

            h.coordinator.start()
            advanceUntilIdle()

            assertEquals(listOf(0), h.api.offsets)
            assertFalse(h.coordinator.syncing.value)
        }

    @Test
    fun aPullRunsAfterTheImportFinished() =
        runTest {
            val h = harness()
            h.import.coordinator.start()
            advanceUntilIdle()

            val result = h.coordinator.pull()

            assertEquals(MalPullResult.DONE, result)
            assertEquals(listOf(0, 0), h.api.offsets)
        }

    @Test
    fun observeEmitsTheStoredTimeAndTheSyncingFlag() =
        runTest {
            val h = harness()
            val observe = ObserveMalPullUseCase(h.pullStore, h.coordinator)
            val gate = h.gatePull()

            observe().test {
                assertEquals(MalPullInfo(lastSyncedAt = null, syncing = false), awaitItem())
                h.coordinator.start()
                assertEquals(MalPullInfo(lastSyncedAt = null, syncing = true), awaitItem())
                gate.complete(Unit)
                var latest = awaitItem()
                while (latest.syncing) latest = awaitItem()
                assertEquals(MalPullInfo(lastSyncedAt = PULL_NOW, syncing = false), latest)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun observeReflectsAStoredTimeRightAway() =
        runTest {
            val h = harness()
            h.pullStore.setLastPulledAt(PULL_NOW - 1.hours)

            ObserveMalPullUseCase(h.pullStore, h.coordinator)().test {
                assertEquals(MalPullInfo(PULL_NOW - 1.hours, syncing = false), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun syncMalNowStartsAPull() =
        runTest {
            val h = harness()

            SyncMalNowUseCase(h.coordinator)()
            advanceUntilIdle()

            assertEquals(listOf(0), h.api.offsets)
            assertEquals(listOf(PULL_NOW), h.pullStore.saved)
        }

    @Test
    fun syncMalNowWhileSyncingDoesNotStartASecondPull() =
        runTest {
            val h = harness()
            val gate = h.gatePull()
            val syncNow = SyncMalNowUseCase(h.coordinator)

            syncNow()
            syncNow()
            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(listOf(0), h.api.offsets)
        }
}
