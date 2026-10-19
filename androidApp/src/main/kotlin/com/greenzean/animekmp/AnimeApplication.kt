package com.greenzean.animekmp

import android.app.Application
import com.greenzean.animekmp.shared.initKoin
import com.greenzean.animekmp.shared.sync.data.ServerConfig
import com.greenzean.animekmp.shared.sync.domain.SyncScheduler
import com.greenzean.animekmp.sync.WorkManagerSyncScheduler
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

class AnimeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidContext(this@AnimeApplication)
            modules(
                module {
                    single { ServerConfig(BuildConfig.GRAPHQL_URL) }
                    single<SyncScheduler> { WorkManagerSyncScheduler(get()) }
                },
            )
        }
        get<SyncScheduler>().requestSync()
    }
}
