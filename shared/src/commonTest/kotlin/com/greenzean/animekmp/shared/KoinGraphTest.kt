package com.greenzean.animekmp.shared

import app.cash.sqldelight.db.SqlDriver
import com.apollographql.apollo.ApolloClient
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
import com.greenzean.animekmp.shared.conflict.presentation.ConflictsViewModel
import com.greenzean.animekmp.shared.conflict.presentation.conflictModule
import com.greenzean.animekmp.shared.core.data.coreDataModule
import com.greenzean.animekmp.shared.core.data.createTestDriver
import com.greenzean.animekmp.shared.core.domain.EmptyResult
import com.greenzean.animekmp.shared.core.domain.Result
import com.greenzean.animekmp.shared.core.logging.AppLogger
import com.greenzean.animekmp.shared.core.logging.LogLevel
import com.greenzean.animekmp.shared.core.logging.LogSink
import com.greenzean.animekmp.shared.core.logging.RecordingLogSink
import com.greenzean.animekmp.shared.core.logging.loggingModule
import com.greenzean.animekmp.shared.details.presentation.DetailsViewModel
import com.greenzean.animekmp.shared.details.presentation.detailsPresentationModule
import com.greenzean.animekmp.shared.export.domain.ExportError
import com.greenzean.animekmp.shared.export.domain.ExportFileSaver
import com.greenzean.animekmp.shared.export.presentation.exportPresentationModule
import com.greenzean.animekmp.shared.importer.domain.ImportError
import com.greenzean.animekmp.shared.importer.domain.ImportFileReader
import com.greenzean.animekmp.shared.importer.presentation.importPresentationModule
import com.greenzean.animekmp.shared.library.data.libraryDataModule
import com.greenzean.animekmp.shared.library.domain.AddToLibraryUseCase
import com.greenzean.animekmp.shared.library.domain.EditEntryUseCase
import com.greenzean.animekmp.shared.library.domain.LibraryLocalDataSource
import com.greenzean.animekmp.shared.library.domain.LibrarySettings
import com.greenzean.animekmp.shared.library.domain.LibrarySort
import com.greenzean.animekmp.shared.library.domain.ObserveLibraryIsEmptyUseCase
import com.greenzean.animekmp.shared.library.domain.ObserveLibrarySortUseCase
import com.greenzean.animekmp.shared.library.domain.ObserveLibraryUseCase
import com.greenzean.animekmp.shared.library.domain.RemoveFromLibraryUseCase
import com.greenzean.animekmp.shared.library.domain.RestoreEntryUseCase
import com.greenzean.animekmp.shared.library.domain.SetLibrarySortUseCase
import com.greenzean.animekmp.shared.library.presentation.LibraryViewModel
import com.greenzean.animekmp.shared.library.presentation.libraryPresentationModule
import com.greenzean.animekmp.shared.mal.data.malModule
import com.greenzean.animekmp.shared.mal.domain.MalAccountStore
import com.greenzean.animekmp.shared.mal.domain.MalAnimeRepository
import com.greenzean.animekmp.shared.mal.domain.MalAuthError
import com.greenzean.animekmp.shared.mal.domain.MalAuthManager
import com.greenzean.animekmp.shared.mal.domain.MalAuthorizer
import com.greenzean.animekmp.shared.mal.domain.MalConfig
import com.greenzean.animekmp.shared.mal.domain.MalRedirectHandler
import com.greenzean.animekmp.shared.mal.domain.MalSendScheduler
import com.greenzean.animekmp.shared.mal.domain.MalSession
import com.greenzean.animekmp.shared.mal.domain.MalTokenStore
import com.greenzean.animekmp.shared.mal.domain.RandomBytes
import com.greenzean.animekmp.shared.mal.presentation.MalAccountViewModel
import com.greenzean.animekmp.shared.mal.presentation.MalImportViewModel
import com.greenzean.animekmp.shared.mal.presentation.malPresentationModule
import com.greenzean.animekmp.shared.search.presentation.SearchViewModel
import com.greenzean.animekmp.shared.search.presentation.searchPresentationModule
import com.greenzean.animekmp.shared.settings.domain.DisplaySettings
import com.greenzean.animekmp.shared.settings.domain.TitleLanguage
import com.greenzean.animekmp.shared.settings.presentation.DisplaySettingsViewModel
import com.greenzean.animekmp.shared.settings.presentation.settingsPresentationModule
import com.greenzean.animekmp.shared.suggestions.presentation.SuggestionsViewModel
import com.greenzean.animekmp.shared.suggestions.presentation.suggestionsPresentationModule
import com.greenzean.animekmp.shared.sync.data.ServerConfig
import com.greenzean.animekmp.shared.sync.data.syncDataModule
import com.greenzean.animekmp.shared.sync.domain.SyncEngine
import com.greenzean.animekmp.shared.sync.domain.SyncScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.core.KoinApplication
import org.koin.core.module.Module
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class KoinGraphTest {
    private val firstSink = RecordingLogSink()
    private val secondSink = RecordingLogSink(minLevel = LogLevel.Info)

    private val hostProvided: Module =
        module {
            single(named("first")) { firstSink } bind LogSink::class
            single(named("second")) { secondSink } bind LogSink::class
            single<SqlDriver> { createTestDriver() }
            single<DisplaySettings> {
                object : DisplaySettings {
                    override val titleLanguage = MutableStateFlow(TitleLanguage.entries.first())

                    override fun setTitleLanguage(language: TitleLanguage) = Unit
                }
            }
            single<MalAccountStore> {
                object : MalAccountStore {
                    override val consentGiven = MutableStateFlow(false)
                    override val username = MutableStateFlow<String?>(null)

                    override fun setConsent(given: Boolean) = Unit

                    override fun setUsername(name: String?) = Unit
                }
            }
            single<ExportFileSaver> {
                object : ExportFileSaver {
                    override suspend fun save(
                        destination: String,
                        content: String,
                    ): EmptyResult<ExportError> = Result.Success(Unit)
                }
            }
            single<ImportFileReader> {
                object : ImportFileReader {
                    override suspend fun read(source: String): Result<ByteArray, ImportError> =
                        Result.Success(ByteArray(0))
                }
            }
            single<LibrarySettings> {
                object : LibrarySettings {
                    override val sort = MutableStateFlow(LibrarySort.LAST_UPDATED)

                    override fun setSort(sort: LibrarySort) = Unit
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
            single<MalSendScheduler> {
                object : MalSendScheduler {
                    override fun requestSend() = Unit
                }
            }
            single<GoogleIdTokenProvider> { GoogleIdTokenProvider { Result.Error(GoogleSignInError.NOT_CONFIGURED) } }
        }

    private fun app(): KoinApplication =
        koinApplication {
            modules(
                hostProvided,
                loggingModule,
                coreDataModule,
                libraryDataModule,
                syncDataModule,
                malModule,
                authModule,
                libraryPresentationModule,
                detailsPresentationModule,
                searchPresentationModule,
                suggestionsPresentationModule,
                malPresentationModule,
                settingsPresentationModule,
                exportPresentationModule,
                importPresentationModule,
                conflictModule,
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
            ObserveLibraryIsEmptyUseCase::class,
            ObserveLibrarySortUseCase::class,
            SetLibrarySortUseCase::class,
            AddToLibraryUseCase::class,
            EditEntryUseCase::class,
            RemoveFromLibraryUseCase::class,
            RestoreEntryUseCase::class,
            LibraryLocalDataSource::class,
            SyncEngine::class,
            SessionManager::class,
            MalAnimeRepository::class,
            MalAuthManager::class,
            MalRedirectHandler::class,
            ApolloClient::class,
        ).forEach { assertNotNull(app.koin.get(it), it.simpleName) }
        app.close()
    }

    @Test
    fun theLoggerResolvesWithEveryBoundSink() {
        val app = app()

        assertEquals(2, app.koin.getAll<LogSink>().size)
        val logger = app.koin.get<AppLogger>()
        logger.debug("T", "debug")
        logger.warn("T", "warn")

        assertEquals(listOf("debug", "warn"), firstSink.entries.map { it.message })
        assertEquals(listOf("warn"), secondSink.entries.map { it.message })
        app.close()
    }

    @Test
    fun everyViewModelResolves() {
        val app = app()
        assertNotNull(app.koin.get<LibraryViewModel>())
        assertNotNull(app.koin.get<SearchViewModel>())
        assertNotNull(app.koin.get<SuggestionsViewModel>())
        assertNotNull(app.koin.get<DisplaySettingsViewModel>())
        assertNotNull(app.koin.get<AccountViewModel>())
        assertNotNull(app.koin.get<MalAccountViewModel>())
        assertNotNull(app.koin.get<MalImportViewModel>())
        assertNotNull(app.koin.get<ConflictsViewModel>())
        assertNotNull(app.koin.get<DetailsViewModel> { parametersOf(MAL_ID) })
        app.close()
    }

    private companion object {
        const val MAL_ID = 1
    }
}
