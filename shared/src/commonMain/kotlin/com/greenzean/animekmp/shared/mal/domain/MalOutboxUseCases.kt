package com.greenzean.animekmp.shared.mal.domain

import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.sync.domain.SyncEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

private val PULL_BEFORE_SEND_AFTER = 1.minutes

class QueueMalChangeUseCase(
    private val outbox: MalOutboxLocalDataSource,
    private val auth: MalAuthManager,
    private val scheduler: MalSendScheduler,
) {
    suspend operator fun invoke(change: MalChange) {
        if (!auth.isLoggedIn.first()) return
        outbox.enqueue(change)
        scheduler.requestSend()
    }
}

class QueueAppOnlyEntriesUseCase(
    private val outbox: MalOutboxLocalDataSource,
    private val auth: MalAuthManager,
    private val scheduler: MalSendScheduler,
    private val syncEngine: SyncEngine,
) {
    suspend operator fun invoke() {
        if (!auth.isLoggedIn.first()) return
        syncEngine.sync()
        outbox.enqueueAppOnlyEntries()
        scheduler.requestSend()
    }
}

class SendMalChangesUseCase(
    private val outbox: MalOutboxLocalDataSource,
    private val api: MalListApi,
    private val throttle: MalThrottle,
    private val auth: MalAuthManager,
    private val baseStore: MalBaseStore,
    private val pullStore: MalPullStore,
    private val pullCoordinator: MalPullCoordinator,
    private val clock: Clock = Clock.System,
) {
    suspend operator fun invoke(): MalSendOutcome =
        if (auth.isLoggedIn.first() && outbox.oldest() != null) {
            if (pullIfStale() == MalPullResult.RETRY) MalSendOutcome.RETRY else drain()
        } else {
            MalSendOutcome.DONE
        }

    private suspend fun pullIfStale(): MalPullResult {
        val last = pullStore.lastPulledAt()
        val stale = last == null || clock.now() - last > PULL_BEFORE_SEND_AFTER
        return if (stale) pullCoordinator.pull() else MalPullResult.DONE
    }

    private suspend fun drain(): MalSendOutcome {
        var outcome = MalSendOutcome.DONE
        var item = outbox.oldest()
        while (item != null && outcome == MalSendOutcome.DONE) {
            outcome = send(item)
            item = if (outcome == MalSendOutcome.DONE) outbox.oldest() else null
        }
        return outcome
    }

    private suspend fun recordBase(change: MalChange) {
        when (change) {
            is MalChange.Upsert -> {
                baseStore.save(
                    change.malId,
                    MalBaseValues(change.status, change.progress, change.score),
                )
            }

            is MalChange.Remove -> {
                baseStore.delete(change.malId)
            }
        }
    }

    private suspend fun send(item: QueuedMalChange): MalSendOutcome {
        val result =
            throttle.run {
                when (val change = item.change) {
                    is MalChange.Upsert -> api.update(change.malId, change.status, change.progress, change.score)
                    is MalChange.Remove -> api.delete(change.malId)
                }
            }
        return when (result) {
            is Result.Success -> {
                outbox.markSent(item)
                recordBase(item.change)
                MalSendOutcome.DONE
            }

            is Result.Error -> {
                val error = result.error
                if (error == MalError.NotFound) {
                    if (item.change is MalChange.Remove) {
                        outbox.markSent(item)
                        baseStore.delete(item.change.malId)
                    } else {
                        outbox.drop(item)
                    }
                    MalSendOutcome.DONE
                } else {
                    outbox.markFailed(item, error.toSendError())
                    MalSendOutcome.RETRY
                }
            }
        }
    }
}

private fun MalError.toSendError(): MalSendError =
    when (this) {
        MalError.NoConnection -> MalSendError.NO_CONNECTION
        is MalError.Throttled -> MalSendError.THROTTLED
        is MalError.Blocked -> MalSendError.BLOCKED
        MalError.Server -> MalSendError.SERVER
        else -> MalSendError.UNKNOWN
    }

class ObserveMalOutboxUseCase(
    private val outbox: MalOutboxLocalDataSource,
) {
    operator fun invoke(): Flow<MalOutboxStatus> = outbox.observeStatus()
}

class RetryMalSendUseCase(
    private val outbox: MalOutboxLocalDataSource,
    private val scheduler: MalSendScheduler,
) {
    suspend operator fun invoke() {
        outbox.clearErrors()
        scheduler.requestSend()
    }
}
