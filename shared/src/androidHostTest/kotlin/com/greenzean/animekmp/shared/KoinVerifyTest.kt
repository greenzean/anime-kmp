package com.greenzean.animekmp.shared

import app.cash.sqldelight.db.SqlDriver
import com.greenzean.animekmp.shared.auth.domain.GoogleIdTokenProvider
import com.greenzean.animekmp.shared.auth.domain.SessionStore
import com.greenzean.animekmp.shared.auth.presentation.authModule
import com.greenzean.animekmp.shared.core.data.coreDataModule
import com.greenzean.animekmp.shared.core.logging.loggingModule
import com.greenzean.animekmp.shared.details.presentation.detailsPresentationModule
import com.greenzean.animekmp.shared.export.domain.ExportFileSaver
import com.greenzean.animekmp.shared.export.presentation.exportPresentationModule
import com.greenzean.animekmp.shared.importer.domain.ImportFileReader
import com.greenzean.animekmp.shared.importer.presentation.importPresentationModule
import com.greenzean.animekmp.shared.library.data.libraryDataModule
import com.greenzean.animekmp.shared.library.domain.LibrarySettings
import com.greenzean.animekmp.shared.library.presentation.libraryPresentationModule
import com.greenzean.animekmp.shared.mal.data.malModule
import com.greenzean.animekmp.shared.mal.domain.MalAccountStore
import com.greenzean.animekmp.shared.mal.domain.MalAuthorizer
import com.greenzean.animekmp.shared.mal.domain.MalConfig
import com.greenzean.animekmp.shared.mal.domain.MalTokenStore
import com.greenzean.animekmp.shared.mal.domain.RandomBytes
import com.greenzean.animekmp.shared.mal.presentation.malPresentationModule
import com.greenzean.animekmp.shared.search.presentation.searchPresentationModule
import com.greenzean.animekmp.shared.settings.domain.DisplaySettings
import com.greenzean.animekmp.shared.settings.presentation.settingsPresentationModule
import com.greenzean.animekmp.shared.suggestions.presentation.suggestionsPresentationModule
import com.greenzean.animekmp.shared.sync.data.ServerConfig
import com.greenzean.animekmp.shared.sync.data.syncDataModule
import com.greenzean.animekmp.shared.sync.domain.SyncScheduler
import org.koin.dsl.module
import org.koin.test.verify.verify
import kotlin.test.Test

class KoinVerifyTest {
    @Test
    fun everyConstructorDependencyOfTheFullModuleListHasADefinition() {
        module {
            includes(
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
            )
        }.verify(
            extraTypes =
                listOf(
                    SqlDriver::class,
                    DisplaySettings::class,
                    LibrarySettings::class,
                    ExportFileSaver::class,
                    ImportFileReader::class,
                    SessionStore::class,
                    MalTokenStore::class,
                    MalAccountStore::class,
                    RandomBytes::class,
                    ServerConfig::class,
                    MalConfig::class,
                    MalAuthorizer::class,
                    GoogleIdTokenProvider::class,
                    SyncScheduler::class,
                ),
        )
    }
}
