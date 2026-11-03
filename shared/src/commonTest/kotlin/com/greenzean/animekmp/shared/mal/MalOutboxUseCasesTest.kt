package com.greenzean.animekmp.shared.mal

import com.greenzean.animekmp.shared.core.domain.DataError
import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.library.domain.WatchStatus
import com.greenzean.animekmp.shared.mal.domain.MalApiError
import com.greenzean.animekmp.shared.mal.domain.MalBaseValues
import com.greenzean.animekmp.shared.mal.domain.MalChange
import com.greenzean.animekmp.shared.mal.domain.MalOutboxStatus
import com.greenzean.animekmp.shared.mal.domain.MalSendError
import com.greenzean.animekmp.shared.mal.domain.MalSendOutcome
import com.greenzean.animekmp.shared.mal.domain.ObserveMalOutboxUseCase
import com.greenzean.animekmp.shared.mal.domain.RetryMalSendUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MalOutboxUseCasesTest {
    private val upsert = MalChange.Upsert(1, WatchStatus.WATCHING, 5, 7)

    @Test
    fun queueDoesNothingWhenMalIsNotConnected() =
        runTest {
            val h = MalOutboxHarness(loggedIn = false)

            h.queue(upsert)

            assertTrue(h.outbox.enqueued.isEmpty())
            assertEquals(0, h.scheduler.requests)
        }

    @Test
    fun queueEnqueuesTheChangeThenRequestsASendWhenConnected() =
        runTest {
            val h = MalOutboxHarness()

            h.queue(upsert)

            assertEquals(listOf<MalChange>(upsert), h.outbox.enqueued)
            assertEquals(listOf("enqueue:1", "requestSend"), h.log)
        }

    @Test
    fun queueAcceptsARemove() =
        runTest {
            val h = MalOutboxHarness()

            h.queue(MalChange.Remove(1))

            assertEquals(listOf<MalChange>(MalChange.Remove(1)), h.outbox.enqueued)
        }

    @Test
    fun queueStartsWorkingOnceMalIsConnected() =
        runTest {
            val h = MalOutboxHarness(loggedIn = false)
            h.queue(upsert)

            h.tokenStore.save(validMalSession())
            h.queue(upsert)

            assertEquals(1, h.outbox.enqueued.size)
            assertEquals(1, h.scheduler.requests)
        }

    @Test
    fun queueAppOnlyDoesNothingWhenMalIsNotConnected() =
        runTest {
            val h = MalOutboxHarness(loggedIn = false)

            h.queueAppOnly()

            assertTrue(h.log.isEmpty())
        }

    @Test
    fun queueAppOnlySyncsBeforeItQueuesAndThenRequestsASend() =
        runTest {
            val h = MalOutboxHarness()

            h.queueAppOnly()

            assertEquals(listOf("sync", "enqueueAppOnly", "requestSend"), h.log)
        }

    @Test
    fun queueAppOnlyStillQueuesWhenTheSyncFails() =
        runTest {
            val h = MalOutboxHarness()
            h.syncApi.error = DataError.Network.NO_INTERNET

            h.queueAppOnly()

            assertEquals(listOf("sync", "enqueueAppOnly", "requestSend"), h.log)
        }

    @Test
    fun observeForwardsTheOutboxStatus() =
        runTest {
            val outbox = FakeMalOutbox()
            outbox.emit(pendingCount = 4, lastError = MalSendError.BLOCKED)

            assertEquals(MalOutboxStatus(4, MalSendError.BLOCKED), ObserveMalOutboxUseCase(outbox)().first())
        }

    @Test
    fun retryClearsTheErrorsThenRequestsASend() =
        runTest {
            val h = MalOutboxHarness()

            RetryMalSendUseCase(h.outbox, h.scheduler)()

            assertEquals(listOf("clearErrors", "requestSend"), h.log)
        }

    private class SendHarness(
        scope: TestScope,
        loggedIn: Boolean = true,
    ) {
        private val pullHarness = MalPullHarness(scope.backgroundScope, loggedIn, lastPulledAt = PULL_NOW)
        val outbox = pullHarness.outbox
        val api = pullHarness.api
        val baseStore = pullHarness.baseStore
        val send = pullHarness.send
    }

    @Test
    fun sendOfAnEmptyQueueIsDoneAndCallsNothing() =
        runTest {
            val h = SendHarness(this)

            assertEquals(MalSendOutcome.DONE, h.send())

            assertTrue(h.api.updates.isEmpty())
            assertTrue(h.api.deletes.isEmpty())
        }

    @Test
    fun sendDoesNothingWhenMalIsNotConnected() =
        runTest {
            val h = SendHarness(this, loggedIn = false)
            h.outbox.enqueue(upsert)

            assertEquals(MalSendOutcome.DONE, h.send())

            assertTrue(h.api.updates.isEmpty())
            assertEquals(listOf<MalChange>(upsert), h.outbox.pending)
        }

    @Test
    fun sendUpdatesMalWithTheQueuedSnapshotAndMarksItSent() =
        runTest {
            val h = SendHarness(this)
            h.outbox.enqueue(upsert)

            assertEquals(MalSendOutcome.DONE, h.send())

            assertEquals(listOf(upsert), h.api.updates)
            assertEquals(listOf<MalChange>(upsert), h.outbox.sent)
            assertTrue(h.outbox.pending.isEmpty())
        }

    @Test
    fun sendOfARemoveDeletesOnMalAndMarksItSent() =
        runTest {
            val h = SendHarness(this)
            h.outbox.enqueue(MalChange.Remove(7))

            assertEquals(MalSendOutcome.DONE, h.send())

            assertEquals(listOf(7), h.api.deletes)
            assertTrue(h.api.updates.isEmpty())
            assertEquals(listOf<MalChange>(MalChange.Remove(7)), h.outbox.sent)
        }

    @Test
    fun sendDrainsTheQueueOldestFirst() =
        runTest {
            val h = SendHarness(this)
            h.outbox.enqueue(MalChange.Upsert(3, WatchStatus.WATCHING, 1, null))
            h.outbox.enqueue(MalChange.Remove(2))
            h.outbox.enqueue(MalChange.Upsert(1, WatchStatus.COMPLETED, 12, 9))

            assertEquals(MalSendOutcome.DONE, h.send())

            assertEquals(listOf(3, 1), h.api.updates.map { it.malId })
            assertEquals(listOf(2), h.api.deletes)
            assertEquals(listOf(3, 2, 1), h.outbox.sent.map { it.malId })
        }

    @Test
    fun sendPassesANullScoreThrough() =
        runTest {
            val h = SendHarness(this)
            h.outbox.enqueue(MalChange.Upsert(1, WatchStatus.PLAN_TO_WATCH, 0, null))

            h.send()

            assertNull(
                h.api.updates
                    .single()
                    .score,
            )
        }

    private suspend fun assertFailure(
        apiError: MalApiError,
        expected: MalSendError,
        scope: TestScope,
    ) {
        val h = SendHarness(scope)
        h.outbox.enqueue(upsert)
        h.outbox.enqueue(MalChange.Upsert(2, WatchStatus.WATCHING, 1, null))
        h.api.updateResults[1] = Result.Error(apiError)

        assertEquals(MalSendOutcome.RETRY, h.send())

        assertEquals(listOf<Pair<MalChange, MalSendError>>(upsert to expected), h.outbox.failures)
        assertEquals(expected, h.outbox.errorOf(1))
        assertEquals(listOf<MalChange>(upsert, MalChange.Upsert(2, WatchStatus.WATCHING, 1, null)), h.outbox.pending)
        assertEquals(listOf(1), h.api.updates.map { it.malId })
        assertTrue(h.outbox.sent.isEmpty())
    }

    @Test
    fun noConnectionKeepsTheRowStoresTheErrorAndRetries() =
        runTest { assertFailure(MalApiError.NO_CONNECTION, MalSendError.NO_CONNECTION, this) }

    @Test
    fun throttlingKeepsTheRowStoresTheErrorAndRetries() =
        runTest { assertFailure(MalApiError.THROTTLED, MalSendError.THROTTLED, this) }

    @Test
    fun forbiddenIsReportedAsBlocked() = runTest { assertFailure(MalApiError.FORBIDDEN, MalSendError.BLOCKED, this) }

    @Test
    fun serverErrorKeepsTheRowAndRetries() = runTest { assertFailure(MalApiError.SERVER, MalSendError.SERVER, this) }

    @Test
    fun unknownErrorKeepsTheRowAndRetries() = runTest { assertFailure(MalApiError.UNKNOWN, MalSendError.UNKNOWN, this) }

    @Test
    fun aFailedDeleteKeepsTheRowAndRetries() =
        runTest {
            val h = SendHarness(this)
            h.outbox.enqueue(MalChange.Remove(5))
            h.api.deleteResults[5] = Result.Error(MalApiError.NO_CONNECTION)

            assertEquals(MalSendOutcome.RETRY, h.send())

            assertEquals(MalSendError.NO_CONNECTION, h.outbox.errorOf(5))
            assertEquals(listOf<MalChange>(MalChange.Remove(5)), h.outbox.pending)
        }

    @Test
    fun aRowThatSucceededBeforeTheFailureStaysSent() =
        runTest {
            val h = SendHarness(this)
            h.outbox.enqueue(MalChange.Upsert(1, WatchStatus.WATCHING, 1, null))
            h.outbox.enqueue(MalChange.Upsert(2, WatchStatus.WATCHING, 1, null))
            h.api.updateResults[2] = Result.Error(MalApiError.SERVER)

            assertEquals(MalSendOutcome.RETRY, h.send())

            assertEquals(listOf(1), h.outbox.sent.map { it.malId })
            assertEquals(listOf(2), h.outbox.pending.map { it.malId })
        }

    @Test
    fun deleteNotFoundCountsAsSent() =
        runTest {
            val h = SendHarness(this)
            h.outbox.enqueue(MalChange.Remove(5))
            h.api.deleteResults[5] = Result.Error(MalApiError.NOT_FOUND)

            assertEquals(MalSendOutcome.DONE, h.send())

            assertEquals(listOf<MalChange>(MalChange.Remove(5)), h.outbox.sent)
            assertTrue(h.outbox.failures.isEmpty())
            assertTrue(h.outbox.pending.isEmpty())
        }

    @Test
    fun updateNotFoundDropsTheRowAndKeepsGoing() =
        runTest {
            val h = SendHarness(this)
            h.outbox.enqueue(upsert)
            h.outbox.enqueue(MalChange.Upsert(2, WatchStatus.WATCHING, 1, null))
            h.api.updateResults[1] = Result.Error(MalApiError.NOT_FOUND)

            assertEquals(MalSendOutcome.DONE, h.send())

            assertEquals(listOf<MalChange>(upsert), h.outbox.dropped)
            assertEquals(listOf(2), h.outbox.sent.map { it.malId })
            assertTrue(h.outbox.failures.isEmpty())
            assertTrue(h.outbox.pending.isEmpty())
        }

    @Test
    fun anEditMadeWhileASendIsRunningSurvives() =
        runTest {
            val h = SendHarness(this)
            h.outbox.enqueue(upsert)
            val edited = MalChange.Upsert(1, WatchStatus.WATCHING, 6, 7)
            h.api.onUpdate = { if (h.outbox.enqueued.size == 1) h.outbox.enqueue(edited) }

            h.send()

            assertEquals(listOf(upsert, edited), h.api.updates)
            assertEquals(listOf<MalChange>(upsert, edited), h.outbox.sent)
            assertTrue(h.outbox.pending.isEmpty())
        }

    @Test
    fun aSuccessfulUpsertSavesItsValuesAsTheMalBase() =
        runTest {
            val h = SendHarness(this)
            h.outbox.enqueue(upsert)

            h.send()

            assertEquals(MalBaseValues(WatchStatus.WATCHING, 5, 7), h.baseStore.values[1])
        }

    @Test
    fun aSuccessfulRemoveDeletesTheMalBase() =
        runTest {
            val h = SendHarness(this)
            h.baseStore.save(5, MalBaseValues(WatchStatus.WATCHING, 5, 7))
            h.outbox.enqueue(MalChange.Remove(5))

            h.send()

            assertNull(h.baseStore.get(5))
        }

    @Test
    fun aRemoveThatMalAlreadyLostDeletesTheMalBase() =
        runTest {
            val h = SendHarness(this)
            h.baseStore.save(5, MalBaseValues(WatchStatus.WATCHING, 5, 7))
            h.outbox.enqueue(MalChange.Remove(5))
            h.api.deleteResults[5] = Result.Error(MalApiError.NOT_FOUND)

            h.send()

            assertNull(h.baseStore.get(5))
        }

    @Test
    fun aFailedUpsertLeavesTheMalBaseUntouched() =
        runTest {
            val h = SendHarness(this)
            val before = MalBaseValues(WatchStatus.WATCHING, 4, 7)
            h.baseStore.save(1, before)
            h.outbox.enqueue(upsert)
            h.api.updateResults[1] = Result.Error(MalApiError.SERVER)

            h.send()

            assertEquals(before, h.baseStore.get(1))
            assertEquals(1, h.baseStore.saved.size)
        }

    @Test
    fun aFailedRemoveLeavesTheMalBaseUntouched() =
        runTest {
            val h = SendHarness(this)
            val before = MalBaseValues(WatchStatus.WATCHING, 4, 7)
            h.baseStore.save(5, before)
            h.outbox.enqueue(MalChange.Remove(5))
            h.api.deleteResults[5] = Result.Error(MalApiError.SERVER)

            h.send()

            assertEquals(before, h.baseStore.get(5))
            assertTrue(h.baseStore.deleted.isEmpty())
        }

    @Test
    fun sendResumesAfterAFailureOnceTheErrorIsGone() =
        runTest {
            val h = SendHarness(this)
            h.outbox.enqueue(upsert)
            h.api.updateResults[1] = Result.Error(MalApiError.NO_CONNECTION)
            assertEquals(MalSendOutcome.RETRY, h.send())

            h.api.updateResults.remove(1)

            assertEquals(MalSendOutcome.DONE, h.send())
            assertTrue(h.outbox.pending.isEmpty())
            assertEquals(listOf<MalChange>(upsert), h.outbox.sent)
        }
}
