package com.greenzean.animekmp.shared.mal

import app.cash.sqldelight.db.SqlDriver
import com.greenzean.animekmp.shared.core.data.createTestDriver
import com.greenzean.animekmp.shared.core.data.db.AnimeDatabase
import com.greenzean.animekmp.shared.core.domain.EmptyResult
import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.mal.data.KtorMalAnimeApi
import com.greenzean.animekmp.shared.mal.data.KtorMalAuthApi
import com.greenzean.animekmp.shared.mal.data.SqlDelightMalCache
import com.greenzean.animekmp.shared.mal.data.malModule
import com.greenzean.animekmp.shared.mal.domain.MalAnimeApi
import com.greenzean.animekmp.shared.mal.domain.MalAnimeCache
import com.greenzean.animekmp.shared.mal.domain.MalAnimeRepository
import com.greenzean.animekmp.shared.mal.domain.MalAuthApi
import com.greenzean.animekmp.shared.mal.domain.MalAuthError
import com.greenzean.animekmp.shared.mal.domain.MalAuthManager
import com.greenzean.animekmp.shared.mal.domain.MalAuthorizer
import com.greenzean.animekmp.shared.mal.domain.MalConfig
import com.greenzean.animekmp.shared.mal.domain.MalRedirectHandler
import com.greenzean.animekmp.shared.mal.domain.MalSession
import com.greenzean.animekmp.shared.mal.domain.MalTokenStore
import com.greenzean.animekmp.shared.mal.domain.RandomBytes
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertIs

class MalModuleTest {
    @Test
    fun resolvesTheLoginPartsFromTheModule() {
        val store =
            object : MalTokenStore {
                override val session = MutableStateFlow<MalSession?>(null)

                override suspend fun get(): MalSession? = session.value

                override suspend fun save(session: MalSession): EmptyResult<MalAuthError> = Result.Success(Unit)

                override suspend fun clear() = Unit
            }
        val app =
            koinApplication {
                modules(
                    module {
                        single { MalConfig("client-1", "https://app.example.test/mal-callback") }
                        single<MalTokenStore> { store }
                        single<MalAuthorizer> { MalAuthorizer { _, _ -> Result.Error(MalAuthError.CANCELLED) } }
                        single<RandomBytes> { RandomBytes { ByteArray(it) } }
                    },
                    malModule,
                )
            }

        assertIs<KtorMalAuthApi>(app.koin.get<MalAuthApi>())
        assertIs<MalAuthManager>(app.koin.get<MalAuthManager>())
        assertIs<MalRedirectHandler>(app.koin.get<MalRedirectHandler>())
        app.close()
    }

    @Test
    fun resolvesTheSearchPartsFromTheModule() {
        val driver = createTestDriver()
        val app =
            koinApplication {
                modules(
                    module {
                        single { MalConfig("client-1", "https://app.example.test/mal-callback") }
                        single<SqlDriver> { driver }
                        single { AnimeDatabase(get()) }
                    },
                    malModule,
                )
            }

        assertIs<KtorMalAnimeApi>(app.koin.get<MalAnimeApi>())
        assertIs<SqlDelightMalCache>(app.koin.get<MalAnimeCache>())
        assertIs<MalAnimeRepository>(app.koin.get<MalAnimeRepository>())
        app.close()
        driver.close()
    }
}
