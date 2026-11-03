package com.greenzean.animekmp

import android.app.Application
import com.greenzean.animekmp.auth.CredentialManagerGoogleIdTokenProvider
import com.greenzean.animekmp.auth.CurrentActivityHolder
import com.greenzean.animekmp.mal.CustomTabsMalAuthorizer
import com.greenzean.animekmp.shared.auth.domain.GoogleIdTokenProvider
import com.greenzean.animekmp.shared.initKoin
import com.greenzean.animekmp.shared.mal.domain.MalAuthorizer
import com.greenzean.animekmp.shared.mal.domain.MalConfig
import com.greenzean.animekmp.shared.mal.domain.MalPullScheduler
import com.greenzean.animekmp.shared.mal.domain.MalSendScheduler
import com.greenzean.animekmp.shared.mal.domain.PullMalIfStaleUseCase
import com.greenzean.animekmp.shared.sync.data.ServerConfig
import com.greenzean.animekmp.shared.sync.domain.SyncScheduler
import com.greenzean.animekmp.sync.WorkManagerMalPullScheduler
import com.greenzean.animekmp.sync.WorkManagerMalSendScheduler
import com.greenzean.animekmp.sync.WorkManagerSyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

class AnimeApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        val activities = CurrentActivityHolder()
        registerActivityLifecycleCallbacks(activities)
        initKoin {
            androidContext(this@AnimeApplication)
            modules(
                module {
                    single { ServerConfig(BuildConfig.GRAPHQL_URL) }
                    single<SyncScheduler> { WorkManagerSyncScheduler(get()) }
                    single { WorkManagerMalSendScheduler(get()) }
                    single<MalSendScheduler> { get<WorkManagerMalSendScheduler>() }
                    single<MalPullScheduler> { WorkManagerMalPullScheduler(get()) }
                    single { MalConfig(BuildConfig.MAL_CLIENT_ID, BuildConfig.MAL_REDIRECT_URI) }
                    single<MalAuthorizer> { CustomTabsMalAuthorizer(activities, get()) }
                    single<GoogleIdTokenProvider> {
                        CredentialManagerGoogleIdTokenProvider(activities, BuildConfig.GOOGLE_SERVER_CLIENT_ID)
                    }
                },
            )
        }
        get<SyncScheduler>().requestSync()
        get<WorkManagerMalSendScheduler>().schedulePeriodic()
        get<MalPullScheduler>().schedulePeriodic()
        val pullIfStale = get<PullMalIfStaleUseCase>()
        appScope.launch { pullIfStale() }
    }
}
