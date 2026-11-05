package com.greenzean.animekmp.shared.mal.data

import com.greenzean.animekmp.shared.importer.domain.OnMalLocalDataSource
import com.greenzean.animekmp.shared.mal.domain.LinkedPlatformsRepository
import com.greenzean.animekmp.shared.mal.domain.MalAnimeApi
import com.greenzean.animekmp.shared.mal.domain.MalAnimeCache
import com.greenzean.animekmp.shared.mal.domain.MalAnimeRepository
import com.greenzean.animekmp.shared.mal.domain.MalAuthApi
import com.greenzean.animekmp.shared.mal.domain.MalAuthManager
import com.greenzean.animekmp.shared.mal.domain.MalBaseStore
import com.greenzean.animekmp.shared.mal.domain.MalImportCoordinator
import com.greenzean.animekmp.shared.mal.domain.MalImportProgressStore
import com.greenzean.animekmp.shared.mal.domain.MalListApi
import com.greenzean.animekmp.shared.mal.domain.MalOutboxLocalDataSource
import com.greenzean.animekmp.shared.mal.domain.MalPullCoordinator
import com.greenzean.animekmp.shared.mal.domain.MalPullStore
import com.greenzean.animekmp.shared.mal.domain.MalRedirectHandler
import com.greenzean.animekmp.shared.mal.domain.MalSuggestionsApi
import com.greenzean.animekmp.shared.mal.domain.MalSuggestionsRepository
import com.greenzean.animekmp.shared.mal.domain.MalThrottle
import com.greenzean.animekmp.shared.mal.domain.MalUserApi
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.module

val malModule =
    module {
        single<MalAuthApi> { KtorMalAuthApi(HttpClient { followRedirects = false }, get(), get()) }
        single { MalAuthManager(get(), get(), get(), get(), get(), logger = get()) }
        single { MalRedirectHandler(get()) }
        single<MalAnimeApi> { KtorMalAnimeApi(HttpClient { followRedirects = false }, get(), get()) }
        single<MalSuggestionsApi> { KtorMalSuggestionsApi(HttpClient { followRedirects = false }, get(), get()) }
        single<MalUserApi> { KtorMalUserApi(HttpClient { followRedirects = false }, get(), get()) }
        single<MalListApi> { KtorMalListApi(HttpClient { followRedirects = false }, get(), get()) }
        single<MalBaseStore> { SqlDelightMalBaseStore(get()) }
        single<MalImportProgressStore> { SqlDelightMalImportProgressStore(get()) }
        single<OnMalLocalDataSource> { SqlDelightOnMalDataSource(get()) }
        single { MalImportCoordinator(get(), CoroutineScope(SupervisorJob() + Dispatchers.Default)) }
        single<MalAnimeCache> { SqlDelightMalCache(get()) }
        single<MalOutboxLocalDataSource> { SqlDelightMalOutboxDataSource(get()) }
        single<MalPullStore> { SqlDelightMalPullStore(get()) }
        single { MalPullCoordinator(get(), get(), CoroutineScope(SupervisorJob() + Dispatchers.Default)) }
        single<LinkedPlatformsRepository> { ApolloLinkedPlatformsRepository(get(), get(), get()) }
        single { MalThrottle(logger = get()) }
        single { MalAnimeRepository(get(), get(), get(), logger = get()) }
        single { MalSuggestionsRepository(get(), get(), get(), logger = get()) }
    }
