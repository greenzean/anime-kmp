package com.greenzean.animekmp.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.greenzean.animekmp.shared.mal.domain.MalSendOutcome
import com.greenzean.animekmp.shared.mal.domain.MalSendScheduler
import com.greenzean.animekmp.shared.mal.domain.SendMalChangesUseCase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

private const val UNIQUE_WORK_NAME = "mal-send"
private const val PERIODIC_WORK_NAME = "mal-send-periodic"
private const val BACKOFF_SECONDS = 30L
private const val PERIODIC_HOURS = 6L

class WorkManagerMalSendScheduler(
    private val context: Context,
) : MalSendScheduler {
    private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    override fun requestSend() {
        val request =
            OneTimeWorkRequestBuilder<MalSendWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .build()
        WorkManager
            .getInstance(context)
            .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    fun schedulePeriodic() {
        val request =
            PeriodicWorkRequestBuilder<MalSendWorker>(PERIODIC_HOURS, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
        WorkManager
            .getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

class MalSendWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params),
    KoinComponent {
    private val sendMalChanges: SendMalChangesUseCase by inject()

    override suspend fun doWork(): Result =
        when (sendMalChanges()) {
            MalSendOutcome.DONE -> Result.success()
            MalSendOutcome.RETRY -> Result.retry()
        }
}
