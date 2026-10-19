package com.greenzean.animekmp.shared.sync.data

import com.apollographql.apollo.ApolloClient
import com.greenzean.animekmp.shared.sync.domain.SyncApi
import com.greenzean.animekmp.shared.sync.domain.SyncEngine
import com.greenzean.animekmp.shared.sync.domain.SyncLocalDataSource
import org.koin.dsl.module

val syncDataModule =
    module {
        single { ApolloClient.Builder().serverUrl(get<ServerConfig>().graphqlUrl).build() }
        single<SyncApi> { ApolloSyncApi(get()) }
        single<SyncLocalDataSource> { SqlDelightSyncLocalDataSource(get()) }
        single { SyncEngine(get(), get()) }
    }
