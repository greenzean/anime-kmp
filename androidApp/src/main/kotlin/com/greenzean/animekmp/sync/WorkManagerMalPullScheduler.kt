package com.greenzean.animekmp.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.greenzean.animekmp.shared.mal.domain.MalPullCoordinator
import com.greenzean.animekmp.shared.mal.domain.MalPullResult
import com.greenzean.animekmp.shared.mal.domain.MalPullScheduler
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

private const val PERIODIC_WORK_NAME = "mal-pull-periodic"
private const val PERIODIC_HOURS = 6L

class WorkManagerMalPullScheduler(
    private val context: Context,
) : MalPullScheduler {
    override fun schedulePeriodic() {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request =
            PeriodicWorkRequestBuilder<MalPullWorker>(PERIODIC_HOURS, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
        WorkManager
            .getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

class MalPullWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params),
    KoinComponent {
    private val coordinator: MalPullCoordinator by inject()

    override suspend fun doWork(): Result =
        when (coordinator.pull()) {
            MalPullResult.DONE, MalPullResult.SKIPPED -> Result.success()
            MalPullResult.RETRY -> Result.retry()
        }
}
