package com.greenzean.animekmp.shared.mal.presentation

import com.greenzean.animekmp.shared.importer.domain.MergeMalEntryUseCase
import com.greenzean.animekmp.shared.mal.domain.AcceptMalConsentUseCase
import com.greenzean.animekmp.shared.mal.domain.ConnectMalUseCase
import com.greenzean.animekmp.shared.mal.domain.DisconnectMalUseCase
import com.greenzean.animekmp.shared.mal.domain.ImportMalListUseCase
import com.greenzean.animekmp.shared.mal.domain.ObserveMalAccountUseCase
import com.greenzean.animekmp.shared.mal.domain.ObserveMalImportUseCase
import com.greenzean.animekmp.shared.mal.domain.StartMalImportUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val malPresentationModule =
    module {
        factoryOf(::ObserveMalAccountUseCase)
        factoryOf(::AcceptMalConsentUseCase)
        factoryOf(::ConnectMalUseCase)
        factoryOf(::DisconnectMalUseCase)
        factoryOf(::ObserveMalImportUseCase)
        factoryOf(::StartMalImportUseCase)
        factoryOf(::ImportMalListUseCase)
        factoryOf(::MergeMalEntryUseCase)
        viewModelOf(::MalAccountViewModel)
        viewModelOf(::MalImportViewModel)
    }
