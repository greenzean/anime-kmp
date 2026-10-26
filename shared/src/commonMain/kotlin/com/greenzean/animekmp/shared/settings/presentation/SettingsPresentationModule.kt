package com.greenzean.animekmp.shared.settings.presentation

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val settingsPresentationModule =
    module {
        viewModelOf(::DisplaySettingsViewModel)
    }
