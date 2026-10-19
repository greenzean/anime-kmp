package com.greenzean.animekmp.shared.library.data

import app.cash.sqldelight.db.SqlDriver
import com.greenzean.animekmp.shared.core.data.coreDataModule
import com.greenzean.animekmp.shared.core.data.createTestDriver
import com.greenzean.animekmp.shared.library.domain.LibraryLocalDataSource
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertIs

class LibraryDataModuleTest {
    @Test
    fun resolvesDataSourceFromModules() {
        val app =
            koinApplication {
                modules(module { single<SqlDriver> { createTestDriver() } }, coreDataModule, libraryDataModule)
            }

        assertIs<SqlDelightLibraryDataSource>(app.koin.get<LibraryLocalDataSource>())
        app.close()
    }
}
