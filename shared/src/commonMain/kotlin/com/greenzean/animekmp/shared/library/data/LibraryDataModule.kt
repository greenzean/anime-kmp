package com.greenzean.animekmp.shared.library.data

import com.greenzean.animekmp.shared.library.domain.LibraryLocalDataSource
import org.koin.dsl.module

val libraryDataModule =
    module {
        single<LibraryLocalDataSource> { SqlDelightLibraryDataSource(get()) }
    }
