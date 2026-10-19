package com.greenzean.animekmp.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.greenzean.animekmp.shared.sync.domain.SyncEngine
import com.greenzean.animekmp.shared.sync.domain.SyncResult
import com.greenzean.animekmp.shared.sync.domain.SyncScheduler
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private const val UNIQUE_WORK_NAME = "sync"

/** Queues one sync that WorkManager runs once the network is connected, so it also fires after a reconnect. */
class WorkManagerSyncScheduler(
    private val context: Context,
) : SyncScheduler {
    override fun requestSync() {
        val request =
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
        WorkManager
            .getInstance(context)
            .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }
}

class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params),
    KoinComponent {
    private val engine: SyncEngine by inject()

    override suspend fun doWork(): Result =
        when (engine.sync()) {
            is SyncResult.Success -> Result.success()
            is SyncResult.Failure -> Result.retry() // default backoff; tuning is out of scope
        }
}
