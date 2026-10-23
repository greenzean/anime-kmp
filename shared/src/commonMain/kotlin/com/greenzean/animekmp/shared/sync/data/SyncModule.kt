package com.greenzean.animekmp.shared.sync.data

import com.apollographql.apollo.ApolloClient
import com.greenzean.animekmp.shared.auth.data.ApolloSessionRefresher
import com.greenzean.animekmp.shared.auth.domain.SessionManager
import com.greenzean.animekmp.shared.auth.domain.SessionRefresher
import com.greenzean.animekmp.shared.sync.domain.AccountLibraryDataSource
import com.greenzean.animekmp.shared.sync.domain.SyncApi
import com.greenzean.animekmp.shared.sync.domain.SyncEngine
import com.greenzean.animekmp.shared.sync.domain.SyncLocalDataSource
import org.koin.dsl.module

val syncDataModule =
    module {
        single { ApolloClient.Builder().serverUrl(get<ServerConfig>().graphqlUrl).build() }

        single<SessionRefresher> { ApolloSessionRefresher(get()) }
        single {
            val library = get<AccountLibraryDataSource>()
            SessionManager(get(), get(), onSessionEnded = library::detachFromAccount)
        }
        single<SyncApi> { ApolloSyncApi(get(), get()) }
        single<SyncLocalDataSource> { SqlDelightSyncLocalDataSource(get()) }
        single<AccountLibraryDataSource> { SqlDelightAccountLibraryDataSource(get()) }
        single { SyncEngine(get(), get()) }
    }
