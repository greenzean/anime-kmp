package com.greenzean.animekmp.shared.settings.presentation

import com.greenzean.animekmp.shared.settings.domain.ObserveTitleLanguageUseCase
import com.greenzean.animekmp.shared.settings.domain.SetTitleLanguageUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val settingsPresentationModule =
    module {
        factoryOf(::ObserveTitleLanguageUseCase)
        factoryOf(::SetTitleLanguageUseCase)
        viewModelOf(::DisplaySettingsViewModel)
    }
