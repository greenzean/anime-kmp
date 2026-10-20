package com.greenzean.animekmp.shared

import com.greenzean.animekmp.shared.core.data.coreDataModule
import com.greenzean.animekmp.shared.core.data.platformModule
import com.greenzean.animekmp.shared.library.data.libraryDataModule
import com.greenzean.animekmp.shared.library.presentation.libraryPresentationModule
import com.greenzean.animekmp.shared.sync.data.syncDataModule
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration

fun initKoin(config: KoinAppDeclaration? = null): KoinApplication =
    startKoin {
        config?.invoke(this)
        modules(platformModule, coreDataModule, libraryDataModule, syncDataModule, libraryPresentationModule)
    }
