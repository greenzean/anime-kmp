package com.greenzean.animekmp.shared.mal.presentation

import com.greenzean.animekmp.shared.mal.domain.AcceptMalConsentUseCase
import com.greenzean.animekmp.shared.mal.domain.ConnectMalUseCase
import com.greenzean.animekmp.shared.mal.domain.DisconnectMalUseCase
import com.greenzean.animekmp.shared.mal.domain.ObserveMalAccountUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val malPresentationModule =
    module {
        factoryOf(::ObserveMalAccountUseCase)
        factoryOf(::AcceptMalConsentUseCase)
        factoryOf(::ConnectMalUseCase)
        factoryOf(::DisconnectMalUseCase)
        viewModelOf(::MalAccountViewModel)
    }
