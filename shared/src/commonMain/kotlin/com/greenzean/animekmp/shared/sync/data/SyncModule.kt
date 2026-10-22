package com.greenzean.animekmp.shared.sync.data

import com.apollographql.apollo.ApolloClient
import com.greenzean.animekmp.shared.auth.data.ApolloSessionRefresher
import com.greenzean.animekmp.shared.auth.data.InMemorySessionStore
import com.greenzean.animekmp.shared.auth.domain.SessionManager
import com.greenzean.animekmp.shared.auth.domain.SessionRefresher
import com.greenzean.animekmp.shared.auth.domain.SessionStore
import com.greenzean.animekmp.shared.sync.domain.SyncApi
import com.greenzean.animekmp.shared.sync.domain.SyncEngine
import com.greenzean.animekmp.shared.sync.domain.SyncLocalDataSource
import org.koin.dsl.module

val syncDataModule =
    module {
        single { ApolloClient.Builder().serverUrl(get<ServerConfig>().graphqlUrl).build() }
        // M2-6 replaces the in-memory store with a persistent, encrypted one.
        single<SessionStore> { InMemorySessionStore() }
        single<SessionRefresher> { ApolloSessionRefresher(get()) }
        single { SessionManager(get(), get()) }
        single<SyncApi> { ApolloSyncApi(get(), get()) }
        single<SyncLocalDataSource> { SqlDelightSyncLocalDataSource(get()) }
        single { SyncEngine(get(), get()) }
    }
