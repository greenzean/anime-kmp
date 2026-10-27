package com.greenzean.animekmp.shared.library.presentation

import com.greenzean.animekmp.shared.library.domain.AddToLibraryUseCase
import com.greenzean.animekmp.shared.library.domain.EditEntryUseCase
import com.greenzean.animekmp.shared.library.domain.ObserveLibraryUseCase
import com.greenzean.animekmp.shared.library.domain.RemoveFromLibraryUseCase
import com.greenzean.animekmp.shared.library.domain.RestoreEntryUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val libraryPresentationModule =
    module {
        factoryOf(::ObserveLibraryUseCase)
        factoryOf(::AddToLibraryUseCase)
        factoryOf(::EditEntryUseCase)
        factoryOf(::RemoveFromLibraryUseCase)
        factoryOf(::RestoreEntryUseCase)
        viewModelOf(::LibraryViewModel)
    }
