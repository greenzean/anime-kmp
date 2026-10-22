package com.greenzean.animekmp.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.greenzean.animekmp.shared.core.domain.DataError
import com.greenzean.animekmp.shared.sync.domain.SyncEngine
import com.greenzean.animekmp.shared.sync.domain.SyncResult
import com.greenzean.animekmp.shared.sync.domain.SyncScheduler
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

private const val UNIQUE_WORK_NAME = "sync"
private const val BACKOFF_SECONDS = 30L

/**
 * Queues a sync that WorkManager runs once the network is connected, so it also fires after a reconnect.
 * A request made while a sync is queued or running is chained after it, so no change waits for the next trigger.
 * Failures retry with a short linear backoff so a reconnect is not stuck behind a long exponential delay.
 */
class WorkManagerSyncScheduler(
    private val context: Context,
) : SyncScheduler {
    override fun requestSync() {
        val request =
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.LINEAR, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .build()
        WorkManager
            .getInstance(context)
            .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }
}

class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params),
    KoinComponent {
    private val engine: SyncEngine by inject()

    override suspend fun doWork(): Result =
        when (val result = engine.sync()) {
            is SyncResult.Success -> {
                Result.success()
            }

            // Signed out or session ended: retrying cannot help. Signing in requests a new sync.
            is SyncResult.Failure -> {
                if (result.error == DataError.Network.UNAUTHORIZED) {
                    Result.failure()
                } else {
                    Result.retry() // linear backoff set in requestSync
                }
            }
        }
}
