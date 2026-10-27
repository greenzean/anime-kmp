package com.greenzean.animekmp.shared

import app.cash.sqldelight.db.SqlDriver
import com.greenzean.animekmp.shared.auth.domain.DeleteAccountUseCase
import com.greenzean.animekmp.shared.auth.domain.GoogleIdTokenProvider
import com.greenzean.animekmp.shared.auth.domain.GoogleSignInError
import com.greenzean.animekmp.shared.auth.domain.MergeLibraryUseCase
import com.greenzean.animekmp.shared.auth.domain.Session
import com.greenzean.animekmp.shared.auth.domain.SessionManager
import com.greenzean.animekmp.shared.auth.domain.SessionStore
import com.greenzean.animekmp.shared.auth.domain.SignInWithGoogleUseCase
import com.greenzean.animekmp.shared.auth.domain.SignOutUseCase
import com.greenzean.animekmp.shared.auth.presentation.AccountViewModel
import com.greenzean.animekmp.shared.auth.presentation.authModule
import com.greenzean.animekmp.shared.core.data.coreDataModule
import com.greenzean.animekmp.shared.core.data.createTestDriver
import com.greenzean.animekmp.shared.core.domain.EmptyResult
import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.details.presentation.DetailsViewModel
import com.greenzean.animekmp.shared.details.presentation.detailsPresentationModule
import com.greenzean.animekmp.shared.library.data.libraryDataModule
import com.greenzean.animekmp.shared.library.domain.AddToLibraryUseCase
import com.greenzean.animekmp.shared.library.domain.LibraryLocalDataSource
import com.greenzean.animekmp.shared.library.domain.ObserveLibraryUseCase
import com.greenzean.animekmp.shared.library.presentation.LibraryViewModel
import com.greenzean.animekmp.shared.library.presentation.libraryPresentationModule
import com.greenzean.animekmp.shared.mal.data.malModule
import com.greenzean.animekmp.shared.mal.domain.MalAnimeRepository
import com.greenzean.animekmp.shared.mal.domain.MalAuthError
import com.greenzean.animekmp.shared.mal.domain.MalAuthManager
import com.greenzean.animekmp.shared.mal.domain.MalAuthorizer
import com.greenzean.animekmp.shared.mal.domain.MalConfig
import com.greenzean.animekmp.shared.mal.domain.MalRedirectHandler
import com.greenzean.animekmp.shared.mal.domain.MalSession
import com.greenzean.animekmp.shared.mal.domain.MalTokenStore
import com.greenzean.animekmp.shared.mal.domain.RandomBytes
import com.greenzean.animekmp.shared.search.presentation.SearchViewModel
import com.greenzean.animekmp.shared.search.presentation.searchPresentationModule
import com.greenzean.animekmp.shared.settings.domain.DisplaySettings
import com.greenzean.animekmp.shared.settings.domain.TitleLanguage
import com.greenzean.animekmp.shared.settings.presentation.DisplaySettingsViewModel
import com.greenzean.animekmp.shared.settings.presentation.settingsPresentationModule
import com.greenzean.animekmp.shared.sync.data.ServerConfig
import com.greenzean.animekmp.shared.sync.data.syncDataModule
import com.greenzean.animekmp.shared.sync.domain.SyncEngine
import com.greenzean.animekmp.shared.sync.domain.SyncScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.core.KoinApplication
import org.koin.core.module.Module
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertNotNull

class KoinGraphTest {
    private val hostProvided: Module =
        module {
            single<SqlDriver> { createTestDriver() }
            single<DisplaySettings> {
                object : DisplaySettings {
                    override val titleLanguage = MutableStateFlow(TitleLanguage.entries.first())

                    override fun setTitleLanguage(language: TitleLanguage) = Unit
                }
            }
            single<SessionStore> {
                object : SessionStore {
                    override val session = MutableStateFlow<Session?>(null)

                    override suspend fun get(): Session? = session.value

                    override suspend fun save(session: Session) = Unit

                    override suspend fun clear() = Unit
                }
            }
            single<MalTokenStore> {
                object : MalTokenStore {
                    override val session = MutableStateFlow<MalSession?>(null)

                    override suspend fun get(): MalSession? = session.value

                    override suspend fun save(session: MalSession): EmptyResult<MalAuthError> = Result.Success(Unit)

                    override suspend fun clear() = Unit
                }
            }
            single<RandomBytes> { RandomBytes { ByteArray(it) } }
            single { ServerConfig("http://localhost/graphql") }
            single { MalConfig("client-1", "https://app.example.test/mal-callback") }
            single<MalAuthorizer> { MalAuthorizer { _, _ -> Result.Error(MalAuthError.CANCELLED) } }
            single<SyncScheduler> {
                object : SyncScheduler {
                    override fun requestSync() = Unit
                }
            }
            single<GoogleIdTokenProvider> { GoogleIdTokenProvider { Result.Error(GoogleSignInError.NOT_CONFIGURED) } }
        }

    private fun app(): KoinApplication =
        koinApplication {
            modules(
                hostProvided,
                coreDataModule,
                libraryDataModule,
                syncDataModule,
                malModule,
                authModule,
                libraryPresentationModule,
                detailsPresentationModule,
                searchPresentationModule,
                settingsPresentationModule,
            )
        }

    @Test
    fun everyUseCaseAndSharedServiceResolves() {
        val app = app()
        listOf(
            SignInWithGoogleUseCase::class,
            MergeLibraryUseCase::class,
            SignOutUseCase::class,
            DeleteAccountUseCase::class,
            ObserveLibraryUseCase::class,
            AddToLibraryUseCase::class,
            LibraryLocalDataSource::class,
            SyncEngine::class,
            SessionManager::class,
            MalAnimeRepository::class,
            MalAuthManager::class,
            MalRedirectHandler::class,
        ).forEach { assertNotNull(app.koin.get(it), it.simpleName) }
        app.close()
    }

    @Test
    fun everyViewModelResolves() {
        val app = app()
        assertNotNull(app.koin.get<LibraryViewModel>())
        assertNotNull(app.koin.get<SearchViewModel>())
        assertNotNull(app.koin.get<DisplaySettingsViewModel>())
        assertNotNull(app.koin.get<AccountViewModel>())
        assertNotNull(app.koin.get<DetailsViewModel> { parametersOf(MAL_ID) })
        app.close()
    }

    private companion object {
        const val MAL_ID = 1
    }
}
