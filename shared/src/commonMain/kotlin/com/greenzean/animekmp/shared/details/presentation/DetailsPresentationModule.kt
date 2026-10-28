package com.greenzean.animekmp.shared.details.presentation

import com.greenzean.animekmp.shared.details.domain.GetAnimeDetailsUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import kotlin.time.Clock

val detailsPresentationModule =
    module {
        single<Clock> { Clock.System }
        factoryOf(::GetAnimeDetailsUseCase)
        viewModelOf(::DetailsViewModel)
    }
