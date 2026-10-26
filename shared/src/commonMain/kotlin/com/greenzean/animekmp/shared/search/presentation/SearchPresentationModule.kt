package com.greenzean.animekmp.shared.search.presentation

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val searchPresentationModule =
    module {
        viewModelOf(::SearchViewModel)
    }
