package com.greenzean.animekmp.shared.sync.domain

/** Platform seam: WorkManager on Android. Runs [SyncEngine.sync] once the network is available. */
interface SyncScheduler {
    fun requestSync()
}
