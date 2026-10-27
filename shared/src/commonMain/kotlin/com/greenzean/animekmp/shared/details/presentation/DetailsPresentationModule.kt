package com.greenzean.animekmp.shared.details.presentation

import com.greenzean.animekmp.shared.details.domain.GetAnimeDetailsUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val detailsPresentationModule =
    module {
        factoryOf(::GetAnimeDetailsUseCase)
        viewModelOf(::DetailsViewModel)
    }
