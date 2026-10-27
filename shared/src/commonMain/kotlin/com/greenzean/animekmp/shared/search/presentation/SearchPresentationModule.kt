package com.greenzean.animekmp.shared.search.presentation

import com.greenzean.animekmp.shared.search.domain.SearchAnimeUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val searchPresentationModule =
    module {
        factoryOf(::SearchAnimeUseCase)
        viewModelOf(::SearchViewModel)
    }
