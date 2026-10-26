package com.greenzean.animekmp.shared.sync

import app.cash.sqldelight.db.SqlDriver
import com.greenzean.animekmp.shared.auth.data.InMemorySessionStore
import com.greenzean.animekmp.shared.auth.domain.SessionStore
import com.greenzean.animekmp.shared.core.data.coreDataModule
import com.greenzean.animekmp.shared.core.data.createTestDriver
import com.greenzean.animekmp.shared.library.data.libraryDataModule
import com.greenzean.animekmp.shared.mal.data.malModule
import com.greenzean.animekmp.shared.mal.domain.MalConfig
import com.greenzean.animekmp.shared.sync.data.ServerConfig
import com.greenzean.animekmp.shared.sync.data.syncDataModule
import com.greenzean.animekmp.shared.sync.domain.SyncEngine
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertNotNull

class SyncDataModuleTest {
    @Test
    fun resolvesTheSyncEngine() {
        val koin =
            koinApplication {
                modules(
                    module {
                        single<SqlDriver> { createTestDriver() }
                        single { ServerConfig("http://localhost:8080/graphql") }
                        single { MalConfig("client-1", "https://app.example.test/mal-callback") }
                        single<SessionStore> { InMemorySessionStore() }
                    },
                    coreDataModule,
                    libraryDataModule,
                    malModule,
                    syncDataModule,
                )
            }.koin

        assertNotNull(koin.get<SyncEngine>())
    }
}
