package com.greenzean.animekmp.shared.mal

import com.greenzean.animekmp.shared.conflict.values
import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.library.domain.Anime
import com.greenzean.animekmp.shared.library.domain.SyncState
import com.greenzean.animekmp.shared.library.domain.WatchStatus
import com.greenzean.animekmp.shared.mal.domain.MalApiError
import com.greenzean.animekmp.shared.mal.domain.MalBaseValues
import com.greenzean.animekmp.shared.mal.domain.MalChange
import com.greenzean.animekmp.shared.mal.domain.MalSendOutcome
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private val MAL_RECENT = PULL_NOW - 1.hours
private val LOCAL_EDITED = PULL_NOW - 3.hours

@OptIn(ExperimentalCoroutinesApi::class)
class SendMalChangesUseCaseTest {
    private val upsert = MalChange.Upsert(1, WatchStatus.COMPLETED, 983, 9)

    private fun TestScope.harness(
        loggedIn: Boolean = true,
        initialOffset: Int = 0,
        lastPulledAt: Instant? = null,
    ) = MalPullHarness(
        backgroundScope + UnconfinedTestDispatcher(testScheduler),
        loggedIn,
        initialOffset,
        lastPulledAt,
    ).also { h -> h.outbox.heldBack = { malId -> h.conflicts.current.any { it.malId == malId } } }

    private fun MalPullHarness.seedLocal(
        malId: Int,
        status: WatchStatus,
        progress: Int,
        score: Int?,
    ) {
        library.put(
            Anime(malId, "Title $malId", episodes = 1000),
            SyncState.SYNCED,
            status,
            progress,
            score,
            LOCAL_EDITED.toEpochMilliseconds(),
        )
        onMal.active = onMal.active + malId
    }

    @Test
    fun pullsFirstWhenNothingWasPulledYet() =
        runTest {
            val h = harness(lastPulledAt = null)
            h.outbox.enqueue(upsert)

            assertEquals(MalSendOutcome.DONE, h.send())

            assertEquals(listOf(0 to 100), h.api.calls)
            assertEquals(listOf(upsert), h.api.updates)
        }

    @Test
    fun pullsFirstWhenTheLastPullIsOverAMinuteOld() =
        runTest {
            val h = harness(lastPulledAt = PULL_NOW - 1.minutes - 1.seconds)
            h.outbox.enqueue(upsert)

            h.send()

            assertEquals(1, h.api.calls.size)
            assertEquals(listOf(upsert), h.api.updates)
        }

    @Test
    fun doesNotPullWhenTheLastPullIsExactlyAMinuteOld() =
        runTest {
            val h = harness(lastPulledAt = PULL_NOW - 1.minutes)
            h.outbox.enqueue(upsert)

            assertEquals(MalSendOutcome.DONE, h.send())

            assertTrue(h.api.calls.isEmpty())
            assertEquals(listOf(upsert), h.api.updates)
        }

    @Test
    fun doesNotPullWhenTheLastPullIsFresh() =
        runTest {
            val h = harness(lastPulledAt = PULL_NOW - 10.seconds)
            h.outbox.enqueue(upsert)

            h.send()

            assertTrue(h.api.calls.isEmpty())
            assertEquals(listOf(upsert), h.api.updates)
        }

    @Test
    fun anEmptyOutboxIsDoneWithoutPulling() =
        runTest {
            val h = harness(lastPulledAt = null)

            assertEquals(MalSendOutcome.DONE, h.send())

            assertTrue(h.api.calls.isEmpty())
            assertTrue(h.api.updates.isEmpty())
        }

    @Test
    fun loggedOutDoesNotPullNorSend() =
        runTest {
            val h = harness(loggedIn = false, lastPulledAt = null)
            h.outbox.enqueue(upsert)

            assertEquals(MalSendOutcome.DONE, h.send())

            assertTrue(h.api.calls.isEmpty())
            assertTrue(h.api.updates.isEmpty())
            assertEquals(listOf<MalChange>(upsert), h.outbox.pending)
        }

    @Test
    fun aPullThatNeedsARetryReturnsRetryAndSendsNothing() =
        runTest {
            val h = harness(lastPulledAt = null)
            h.outbox.enqueue(upsert)
            h.outbox.enqueue(MalChange.Remove(2))
            h.api.results[0] = Result.Error(MalApiError.NO_CONNECTION)

            assertEquals(MalSendOutcome.RETRY, h.send())

            assertTrue(h.api.updates.isEmpty())
            assertTrue(h.api.deletes.isEmpty())
            assertEquals(2, h.outbox.pending.size)
            assertTrue(h.outbox.failures.isEmpty())
        }

    @Test
    fun aSkippedPullStillSends() =
        runTest {
            val h = harness(initialOffset = 100, lastPulledAt = null)
            h.outbox.enqueue(upsert)

            assertEquals(MalSendOutcome.DONE, h.send())

            assertTrue(h.api.calls.isEmpty())
            assertEquals(listOf(upsert), h.api.updates)
        }

    @Test
    fun aRowThatTheFreshPullTurnedIntoAConflictIsNotSent() =
        runTest {
            val h = harness(lastPulledAt = null)
            h.seedLocal(1, WatchStatus.COMPLETED, 983, 9)
            val other = MalChange.Upsert(2, WatchStatus.WATCHING, 3, null)
            h.outbox.enqueue(upsert)
            h.outbox.enqueue(other)
            h.api.results[0] = pullPage(pullItem(1, WatchStatus.COMPLETED, 985, 9, MAL_RECENT))

            assertEquals(MalSendOutcome.DONE, h.send())

            assertEquals(listOf(other), h.api.updates)
            assertEquals(listOf(1), h.conflicts.saved.map { it.first })
            assertEquals(listOf<MalChange>(upsert), h.outbox.pending)
        }

    @Test
    fun aSuccessfulUpsertSavesTheSentValuesAsTheBase() =
        runTest {
            val h = harness(lastPulledAt = PULL_NOW)
            h.outbox.enqueue(MalChange.Upsert(1, WatchStatus.WATCHING, 5, null))

            h.send()

            assertEquals(MalBaseValues(WatchStatus.WATCHING, 5, null), h.baseStore.values[1])
        }

    @Test
    fun aSuccessfulRemoveDeletesTheBase() =
        runTest {
            val h = harness(lastPulledAt = PULL_NOW)
            h.baseStore.save(4, MalBaseValues(WatchStatus.WATCHING, 5, 7))
            h.outbox.enqueue(MalChange.Remove(4))

            h.send()

            assertEquals(listOf(4), h.baseStore.deleted)
            assertTrue(4 !in h.baseStore.values)
        }

    @Test
    fun aRemoveThatMalAnswersWithNotFoundDeletesTheBase() =
        runTest {
            val h = harness(lastPulledAt = PULL_NOW)
            h.baseStore.save(4, MalBaseValues(WatchStatus.WATCHING, 5, 7))
            h.outbox.enqueue(MalChange.Remove(4))
            h.api.deleteResults[4] = Result.Error(MalApiError.NOT_FOUND)

            assertEquals(MalSendOutcome.DONE, h.send())

            assertTrue(4 !in h.baseStore.values)
        }

    @Test
    fun aFailedSendLeavesTheBaseUntouched() =
        runTest {
            val h = harness(lastPulledAt = PULL_NOW)
            val before = MalBaseValues(WatchStatus.COMPLETED, 982, 9)
            h.baseStore.save(1, before)
            h.outbox.enqueue(upsert)
            h.api.updateResults[1] = Result.Error(MalApiError.SERVER)

            assertEquals(MalSendOutcome.RETRY, h.send())

            assertEquals(before, h.baseStore.values[1])
            assertTrue(h.baseStore.deleted.isEmpty())
        }

    @Test
    fun aChangeMadeOnMalWhileTheAppWasOfflineIsNotOverwritten() =
        runTest {
            val h = harness(lastPulledAt = null)
            h.seedLocal(1, WatchStatus.COMPLETED, 983, 9)
            h.baseStore.save(1, MalBaseValues(WatchStatus.COMPLETED, 982, 9))
            h.outbox.enqueue(upsert)
            h.api.results[0] = pullPage(pullItem(1, WatchStatus.COMPLETED, 985, 9, MAL_RECENT))

            h.send()

            assertTrue(h.api.updates.isEmpty())
            assertEquals(listOf(1 to values(WatchStatus.COMPLETED, 985, 9, removed = false)), h.conflicts.saved)
            assertEquals(listOf(1), h.conflicts.current.map { it.malId })
        }

    @Test
    fun aChangeThatMalStillHasAsTheBaseIsSentWithoutAConflict() =
        runTest {
            val h = harness(lastPulledAt = null)
            h.seedLocal(1, WatchStatus.COMPLETED, 983, 9)
            h.baseStore.save(1, MalBaseValues(WatchStatus.COMPLETED, 982, 9))
            h.outbox.enqueue(upsert)
            h.api.results[0] = pullPage(pullItem(1, WatchStatus.COMPLETED, 982, 9, MAL_RECENT))

            assertEquals(MalSendOutcome.DONE, h.send())

            assertEquals(listOf(upsert), h.api.updates)
            assertTrue(h.conflicts.saved.isEmpty())
            assertTrue(h.conflicts.current.isEmpty())
            assertEquals(MalBaseValues(WatchStatus.COMPLETED, 983, 9), h.baseStore.values[1])
            assertTrue(h.outbox.pending.isEmpty())
        }
}
