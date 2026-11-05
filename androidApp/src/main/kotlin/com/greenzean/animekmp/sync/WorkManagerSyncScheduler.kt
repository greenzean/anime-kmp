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
import com.greenzean.animekmp.shared.core.logging.AppLogger
import com.greenzean.animekmp.shared.mal.domain.BackgroundSyncUseCase
import com.greenzean.animekmp.shared.sync.domain.SyncResult
import com.greenzean.animekmp.shared.sync.domain.SyncScheduler
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

private const val UNIQUE_WORK_NAME = "sync"
private const val BACKOFF_SECONDS = 30L
private const val TAG = "SyncWorker"

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
    private val backgroundSync: BackgroundSyncUseCase by inject()
    private val logger: AppLogger by inject()

    override suspend fun doWork(): Result =
        when (val result = backgroundSync()) {
            is SyncResult.Success -> {
                logger.info(TAG, "sync ok: pushed=${result.pushed} pulled=${result.pulled}")
                Result.success()
            }

            is SyncResult.Failure -> {
                logger.warn(TAG, "sync failed: ${result.error}")
                if (result.error == DataError.Network.UNAUTHORIZED) Result.failure() else Result.retry()
            }
        }
}
